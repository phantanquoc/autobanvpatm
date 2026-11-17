## ADDED Requirements

### Requirement: ATM order creation and quote
The system SHALL when `banvatpham.txt` contains `ATM=1` handle `mua <qty> <loai>` (e.g. `mua 2 ttc`, case-insensitive, qty and loai in any order) by selecting `qty` CHUA_BAN entries of that loai in FIFO order (peekFifoByLoai), computing `totalVnd` as sum of `giaVnd`, generating a unique order code `NIK` + 6 random digits (checked against pending orders for uniqueness), and storing an `AtmOrder { code, buyerName, loai, qty, totalVnd, selected NikEntrys, createdAt, expiresAt = createdAt + 15 minutes }` in a concurrent pending map. The system SHALL then send a private quote containing `Gia <qty> nick loai <loai>: <totalVnd> VND`, STK + bank name, VietQR image URL with `amount=totalVnd` and `addInfo=code`, `ND: <code>`, and `Han 15 phut`. If stock is insufficient the system SHALL reply with available count and SHALL NOT create an order.

#### Scenario: mua 2 ttc creates ATM order
- **WHEN** `ATM=1`, CHUA_BAN has 3 `ttc` entries [10k, 10k, 15k], buyer sends `mua 2 ttc`
- **THEN** the system creates order `NIKxxxxxx` with `totalVnd=20000`, sends a private message containing `20000`, `NIK`, STK, and VietQR URL

#### Scenario: Insufficient stock does not create order
- **WHEN** CHUA_BAN has 1 `ttc` entry and buyer sends `mua 2 ttc`
- **THEN** the system replies `Chi con 1 nick loai ttc chua ban` and does not create an order

### Requirement: SePay webhook verification and matching
The system SHALL expose `POST /webhook/sepay` on the configured port (default 8089) and SHALL verify each request by checking the `Authorization` header or configured `SEPAY_SECRET` (HMAC or token per SePay docs), parsing `amount` (transferAmount), `content` (transferContent/description), and `transaction_id` (id/referenceCode). The system SHALL reject requests with invalid secret, missing amount/transaction_id, or duplicate `transaction_id` already in `seenTransactionIds`. For valid requests the system SHALL search pending orders for a code contained (case-insensitive) in `content`; the first matching order with `amount >= totalVnd` SHALL be fulfilled, otherwise the webhook is logged as unmatched/underpaid.

#### Scenario: Valid webhook fulfills order
- **WHEN** pending order `NIK482913` has `totalVnd=20000` and webhook arrives with `amount=20000`, `content="CK NIK482913"`, `transaction_id=abc123` (new)
- **THEN** the system marks the order as paid and fulfills it

#### Scenario: Replay transaction is rejected
- **WHEN** a webhook with `transaction_id=abc123` was already processed
- **THEN** the second webhook with the same id is ignored and no second delivery occurs

#### Scenario: Underpaid webhook is not fulfilled
- **WHEN** order `totalVnd=20000` and webhook has `amount=10000` with correct code
- **THEN** the system does not fulfill; it logs underpaid and optionally notifies buyer of shortfall

### Requirement: ATM delivery and persistence
The system SHALL upon matching a valid webhook move the order's selected entries from CHUA_BAN to DA_BAN atomically inside `synchronized(store)`, set each `trangThai=1` and `nguoiMua=buyerName`, call `store.save(workDir)`, append to `logs/nik_sold.log` via `NikStore.appendSoldLog`, remove the order from pending, persist `seenTransactionIds` (append to `atm_seen.log`), and deliver credentials to the buyer via private chat `NICK: TK=<tk> MK=<mk> VatPham=<loai> Gia=<giaVnd>` for each entry. Delivery SHALL retry up to 3 times at 5-second intervals if `sendPrivate` fails; if buyer is offline the entries remain DA_BAN and the system logs pending delivery for manual resend.

#### Scenario: Delivery after webhook
- **WHEN** webhook fulfills order `NIK482913` for buyer `playerA` with 2 entries
- **THEN** both entries appear in DA_BAN with `NguoiMua=playerA`, `nik_ban.txt` is updated, `nik_sold.log` is appended, and `playerA` receives 2 private messages with TK/MK

#### Scenario: Delivery retry on failure
- **WHEN** first `sendPrivate` throws
- **THEN** the system retries up to 3 times with 5s delay before giving up and logging

### Requirement: Order timeout and overpay handling
The system SHALL expire pending orders 15 minutes after creation: expired orders are removed from the pending map without moving any NIK to DA_BAN, and the reserved entries become available again. A background sweep SHALL run at least every 60 seconds. Overpaid webhooks (`amount > totalVnd`) SHALL still fulfill (deliver) and the system SHALL include `thua <amount-totalVnd> VND` in the delivery message. Underpaid webhooks SHALL NOT fulfill; the system SHALL notify the buyer `Thieu <total-amount> VND, vui long chuyen them giu nguyen ND: <code>`.

#### Scenario: Order expires after 15 minutes
- **WHEN** order `NIK482913` was created 16 minutes ago and no webhook arrived
- **THEN** the order is removed and its NIKs are available for new orders

#### Scenario: Overpay still delivers
- **WHEN** order `totalVnd=20000` and webhook has `amount=25000` with correct code
- **THEN** the system delivers the NIKs and the message contains `thua 5000`

### Requirement: Feature flag and config
The system SHALL read `ATM` (0/1, default 0), `BANK_CODE`, `BANK_STK`, `BANK_NAME`, `SEPAY_SECRET`, `ATM_PORT` (default 8089) from `banvatpham.txt` (and fallback `config.txt`) via the existing `Class_bb.a(content, tag)` tag parser. When `ATM=0` the system SHALL behave exactly as before (trade xu flow). When `ATM=1` `mua` messages for NIK SHALL take the ATM branch and SHALL NOT invite trade. The HTTP service SHALL start only when `ATM=1` and SHALL log startup errors without crashing the bot.

#### Scenario: ATM=0 preserves trade flow
- **WHEN** `banvatpham.txt` has `ATM=0` or no ATM tag and buyer sends `mua 2 ttc`
- **THEN** the system follows the existing VND→xu→trade flow

#### Scenario: ATM=1 uses ATM branch
- **WHEN** `banvatpham.txt` has `ATM=1` and buyer sends `mua 2 ttc`
- **THEN** the system creates an ATM order and does not send opcode 43
