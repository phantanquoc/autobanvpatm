## Context

`NikSeller` hiện xử lý `mua <qty> [loai]` qua FIFO `nik_ban.txt` → tính `totalVND` → `ceil(total/VND_PER_XU)` → `STATE_WAITING` → mời trade xu (opcode 43) → xác thực `xuKhach >= expectedXu` → `DA_BAN` + whisper TK/MK. `BanSeller` quản lý `STATE_*`, `withdrawing`, rao, rút rương (`cd.q.x`). `NikStore` là `synchronized`, shared static qua `BanSeller.getSharedNikStore()`. `NikFrame` là Swing admin. `banvatpham.txt` chứa `[ITEM]`, `[VND_PER_XU]`, `[NICK]`.

Yêu cầu mới: thêm nhánh ATM khi `ATM=1` — cùng parse FIFO/giá, nhưng thay vì trade xu, tạo đơn `NIKxxxxxx` + QR → chờ SePay webhook → tự giao. Phải giữ nguyên luồng xu khi `ATM=0`. HTTP webhook phải sống độc lập với game loop (poll/block không được chặn `Class_cp` tick).

## Goals / Non-Goals

**Goals:**
- Khách `mua 2 ttc` khi `ATM=1` nhận báo giá VND + STK + VietQR + `ND: NIK...` + hạn 15 phút thay vì lời mời trade.
- SePay `POST /webhook/sepay` được verify (`secret`), chống replay (`transaction_id`), khớp `content contains mã` + `amount >= total`.
- Giao nick nguyên tử: `synchronized(store)` → cập nhật `DA_BAN` + `save` + `appendSoldLog` + whisper TK/MK (retry 3).
- Thiếu/thừa/sai mã/timeout xử lý đúng spec.
- `ATM=0` giữ nguyên hành vi cũ.

**Non-Goals:**
- Không hoàn tiền thừa tự động, không xử lý chargeback.
- Không thay `Class_cq/Class_cp` ngoài route đã có; không thêm DB mới (vẫn file `nik_ban.txt`).
- Không poll trực tiếp API ngân hàng ở phase này (chỉ thiết kế abstraction để thay sau).

## Decisions

**1. SePay webhook (không poll bank)**
- Rationale: bank đổi API/captcha → poll gãy; SePay FREE <50 gd, 120k sau, lo duy trì. Poll 10-15s delay cao hơn webhook 1-3s. Bot không giữ credential bank.
- Alternative: poll `MB/VCB` API → 0đ nhưng phải tự refresh session/OTP, rủi ro khóa TK.

**2. Mã đơn `NIK` + 6 số ngẫu nhiên**
- Rationale: khớp bằng `content contains mã` chính xác, chống trùng tên nhân vật, dễ cho khách copy. Random 6 số + check trùng trong `pendingOrders`.
- Alternative: `MUA <TênNV>` → nhầm do dấu/cách, trùng tên.

**3. HTTP server riêng `AtmPaymentService` (com.sun.net.httpserver.HttpServer, JDK built-in, port 8089)**
- Rationale: không thêm dependency ngoài; chạy daemon thread, không chặn game loop; sống độc lập với `Class_cp` tick. `NikSeller` poll queue mỗi giây hoặc callback trực tiếp (synchronized).
- Alternative: nhúng vào JVM bot poll API → tắt bot là mất sync.

**4. Abstraction `AtmPaymentSource` (interface)**
- Rationale: hôm nay `SepayWebhookSource`, mai đổi `PollingBankSource` không sửa `NikSeller`. Verify/replay logic nằm trong source, giao nick nằm trong `NikSeller.onAtmPaymentVerified`.

**5. Lưu `pendingOrders` (ConcurrentHashMap mã→AtmOrder) + `seenTransactionIds` (Set, persist file `atm_seen.log`)**
- Rationale: concurrent giữa HTTP thread và game thread; persist chống giao trùng sau restart.
- Alternative: chỉ memory → restart mất, bị replay.

**6. VietQR động**
- Format: `https://img.vietqr.io/image/<BANK>-<STK>-compact2.png?amount=<total>&addInfo=<mã>&accountName=<...>` hoặc text fallback. Sinh từ `BANK_*` trong `banvatpham.txt`.
- Rationale: khách quét 1 lần, giảm sai amount/content 90%.

**7. Feature flag `ATM` trong `banvatpham.txt`**
- Rationale: A/B từng bot, rollback tức thì không cần rebuild jar.

## Risks / Trade-offs

- **SePay downtime / webhook miss** → Mitigation: log raw webhook, admin tab hiển thị pending, cho phép resend/xác nhận tay trong `NikFrame`.
- **Replay attack (gửi lại transaction_id)** → Mitigation: `seenTransactionIds` check trước khi giao, persist file.
- **Khách đổi tên sau khi đặt đơn** → Mitigation: khớp theo mã, không theo tên; whisper theo `buyerName` lúc đặt; nếu đổi tên thì đơn vẫn giao cho tên cũ (log rõ), khách phải đặt lại nếu chưa giao.
- **Giá thay đổi giữa đặt và CK** → Mitigation: giá khóa tại lúc tạo đơn (`AtmOrder.totalVnd`).
- **Tiền về thiếu** → Mitigation: giữ đơn, báo thiếu `Xđ`, khách chuyển thêm giữ nguyên mã → cộng dồn check lại (tùy chọn phase 2, phase 1 chỉ báo thiếu).
- **Port 8089 bị chiếm** → Mitigation: cho phép cấu hình `ATM_PORT` trong `banvatpham.txt`, log lỗi không crash bot.

## Migration Plan

1. Thêm tag `ATM`, `BANK_*`, `SEPAY_SECRET`, `ATM_PORT` vào `banvatpham.txt` (mặc định `ATM=0` để an toàn).
2. Build patcher mới, thay jar; khởi động bot → `AtmPaymentService` start nếu `ATM=1`.
3. Cấu hình SePay: liên kết STK riêng → thêm webhook `https://<ip>:8089/webhook/sepay` + secret.
4. Test: `mua 2 ttc` → check whisper báo giá + QR → giả webhook local `curl POST` → verify `nik_ban.txt` + `nik_sold.log` + whisper.
5. Rollback: set `ATM=0` hoặc xóa webhook, luồng xu hoạt động lại ngay.

## Open Questions

- Có cần cộng dồn nhiều lần chuyển khoản cho cùng mã khi thiếu tiền (phase 1 chỉ báo thiếu, phase 2 mới cộng dồn)?
- Có cần UI pending orders trong `NikFrame` ở phase 1 hay chỉ log file?
