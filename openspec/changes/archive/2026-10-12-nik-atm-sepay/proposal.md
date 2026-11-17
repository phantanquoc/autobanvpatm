## Why

Hiện tại NikSeller chỉ bán nick qua trade xu trong game (VND → xu → trade). Khách phải vào game giao dịch, dễ lỗi, chậm và phụ thuộc xu. Cần thêm phương thức thanh toán ATM tự động: khách chat `mua 2 ttc` → bot báo giá VND → khách chuyển khoản đúng nội dung → SePay webhook xác nhận → bot tự giao TK/MK qua whisper, không cần trade xu. Giảm thao tác, tăng tỉ lệ chốt đơn và cho phép bán khi khách không đủ xu.

## What Changes

- Thêm nhánh thanh toán ATM song song với nhánh trade xu trong `NikSeller.handlePrivateMessage` khi `banvatpham.txt` có `ATM=1`.
- Tạo đơn hàng ATM (`AtmOrder`) với mã duy nhất `NIKxxxxxx`, tổng VND (FIFO theo `nik_ban.txt`), hạn 15 phút, lưu `pendingOrders`.
- Gửi báo giá ATM: STK + tên chủ TK + VietQR động (amount + content) + `ND: NIK...` + hạn.
- Dựng HTTP service `AtmPaymentService` (port 8089) nhận `POST /webhook/sepay`, verify `secret`, parse `amount/content/transaction_id`, chống replay bằng `seenTransactionIds`.
- Khớp webhook: `content contains mã` + `amount >= total` + `transaction_id` mới → đánh dấu `CHUA_BAN → DA_BAN` (synchronized NikStore), `save`, `appendSoldLog`, whisper TK/MK (retry 3 lần).
- Xử lý lệch tiền: thiếu → báo thiếu giữ đơn; thừa → giao + báo thừa; sai mã → log; timeout 15 phút → hủy đơn.
- Giữ nguyên luồng trade xu khi `ATM=0` (feature flag).
- Thêm cấu hình `banvatpham.txt`: `ATM`, `BANK_*`, `SEPAY_SECRET`, `VND_PER_XU` đã có.

## Capabilities

### New Capabilities
- `nik-atm-payment`: Thanh toán ATM tự động qua SePay webhook cho bán NIK — tạo đơn, báo giá QR, xác thực webhook, khớp và giao nick, chống replay, timeout.

### Modified Capabilities
- `nik-sales`: Mở rộng routing chat NIK để hỗ trợ nhánh ATM khi flag bật; hành vi FIFO, tính giá, busy guard giữ nguyên nhưng bổ sung nhánh ATM.

## Impact

- Code: `BanVatPham/patcher/NikSeller.java`, `BanSeller.java` (flag ATM), `NikStore.java`/`NikEntry.java` (dùng sẵn), thêm `AtmOrder.java`, `AtmPaymentService.java`, `BanVatPham/patcher/NikFrame.java` (log/tab pending tùy chọn), `banvatpham.txt`.
- Hệ thống: cần SePay liên kết 1 STK riêng, cấu hình webhook URL + secret; HTTP port 8089 phải mở trên máy chạy bot.
- Không phá vỡ luồng xu cũ; không sửa `Class_cq/Class_cp` ngoài route đã có; không hoàn tiền tự động.
