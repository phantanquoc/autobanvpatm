## 1. Config & foundation

- [x] 1.1 Add ATM config tags (`ATM`, `BANK_CODE`, `BANK_STK`, `BANK_NAME`, `SEPAY_SECRET`, `ATM_PORT`) to `banvatpham.txt` handling via `Class_bb.a` parser with defaults (ATM=0, port 8089) in `BanSeller`/`NikSeller`
- [x] 1.2 Create `AtmOrder` model (`BanVatPham/patcher/AtmOrder.java`) with fields code/buyerName/loai/qty/totalVnd/selected/createdAt/expiresAt and helpers
- [x] 1.3 Add concurrent `pendingOrders` map and `seenTransactionIds` set (with file persistence `atm_seen.log`) to NikSeller/BanSeller scope

## 2. ATM order creation & quote (NikSeller branch)

- [x] 2.1 Add ATM branch in `NikSeller.handlePrivateMessage`: when `ATM=1` and message matches `mua <qty> <loai>` (qty/loai any order, loai via `NikStore.getLoaiList`), use `peekFifoByLoai` to select, compute `totalVnd`, check stock, generate unique `NIK`+6 digits
- [x] 2.2 Store AtmOrder in pending map (15-min expiry) and send private quote with STK/bank name/VietQR URL (`amount`+`addInfo`)/`ND: code`/`Han 15 phut` — no trade invite
- [x] 2.3 Ensure `ATM=0` path unchanged (existing VND→xu→trade flow) and routing modification for `mua <loai>` without `nik` token only when `ATM=1` ← (verify: `mua 2 ttc` with ATM=1 creates order, with ATM=0 goes to item flow or existing nik flow)

## 3. SePay webhook service

- [x] 3.1 Create `AtmPaymentService` (`BanVatPham/patcher/AtmPaymentService.java`) using `com.sun.net.httpserver.HttpServer` on `ATM_PORT`, endpoint `POST /webhook/sepay`, start only when `ATM=1`, daemon thread, log errors without crashing bot
- [x] 3.2 Implement request verification: check `Authorization`/`SEPAY_SECRET`, parse `amount/content/transaction_id` supporting SePay JSON variants, reject on invalid secret/missing fields/duplicate `transaction_id`
- [x] 3.3 Implement matching: case-insensitive `content contains code` search over pending orders, `amount >= totalVnd` → fulfill, else log underpaid/unmatched ← (verify: valid webhook fulfills, replay rejected, underpaid not fulfilled)

## 4. Delivery & persistence

- [x] 4.1 Implement `onAtmPaymentVerified` fulfillment: `synchronized(store)` move selected `NikEntry`s to DA_BAN (`trangThai=1`, `nguoiMua`), `store.save`, `appendSoldLog`, remove order, persist `seenTransactionIds`, log raw webhook
- [x] 4.2 Deliver TK/MK via `sendPrivate` with retry (3 times, 5s interval), handle overpay message `thua X VND`, handle offline buyer (keep DA_BAN, log pending) ← (verify: after webhook entries in DA_BAN, file updated, buyer receives messages or logged)

## 5. Timeout, edge cases & integration

- [x] 5.1 Add background sweep (every 60s) to expire orders after 15 minutes (remove without DA_BAN transition), notify-free or log
- [x] 5.2 Handle overpay/underpay per spec: overpay delivers + `thua`, underpay notifies `Thieu ... giu nguyen ND: code` and keeps order
- [x] 5.3 Wire `BanSeller` flag reading, busy guard for ATM pending, and `AtmPaymentService` lifecycle (start/stop) with `Class_cu.g` workDir; add minimal pending log/tab in `NikFrame` if needed (optional)
- [x] 5.4 Manual test: `mua 2 ttc` → quote/Q R, `curl POST /webhook/sepay` valid/replay/underpay/overpay, timeout after 15 min, verify `nik_ban.txt` + `nik_sold.log` + `atm_seen.log` ← (verify: all scenarios from spec pass end-to-end)
