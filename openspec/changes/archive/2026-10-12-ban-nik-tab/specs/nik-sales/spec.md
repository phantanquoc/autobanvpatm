## ADDED Requirements

### Requirement: NIK persistence in nik_ban.txt
The system SHALL persist NIK inventory in `workDir/nik_ban.txt` with one NIK per line in the format `TK:MK:VatPham:GiaVND:TrangThai:NguoiMua` where `TrangThai` is `0` (CHUA_BAN) or `1` (DA_BAN) and `NguoiMua` is empty for unsold entries. The system SHALL create the file on first save if absent and preserve ordering top-to-bottom as display/FIFO order.

#### Scenario: First save creates file
- **WHEN** the admin adds the first NIK and `nik_ban.txt` does not exist
- **THEN** the system creates the file and writes one line for the new NIK with `TrangThai=0`

#### Scenario: Load preserves FIFO order
- **WHEN** the system loads `nik_ban.txt` containing three CHUA_BAN entries in order A, B, C
- **THEN** the in-memory CHUA_BAN list is [A, B, C]

#### Scenario: Malformed line is skipped
- **WHEN** the file contains a line with the wrong number of fields or non-numeric GiaVND/TrangThai
- **THEN** that line is skipped, an error is logged, and remaining lines still load

### Requirement: FIFO purchase via mua nik qty
The system SHALL handle private chat `mua nik <qty>` (case-insensitive, normalized) by selecting the first `qty` entries from CHUA_BAN in display order (FIFO), computing `totalVND` as the sum of their `GiaVND`, and rejecting the request if `qty` is missing (default 1), non-positive, non-integer, or exceeds available unsold count. The `qty` argument is the count of NIKs to buy, not an index.

#### Scenario: mua nik 3 selects first three
- **WHEN** CHUA_BAN contains [N1:50k, N2:70k, N3:100k, N4:60k] and a buyer sends `mua nik 3`
- **THEN** the system selects [N1, N2, N3] and computes `totalVND=220000`

#### Scenario: Insufficient stock is reported
- **WHEN** CHUA_BAN has 2 entries and buyer sends `mua nik 5`
- **THEN** the system replies that only 2 NIKs are available and does not start a trade

#### Scenario: Missing qty defaults to one
- **WHEN** buyer sends `mua nik` with no number
- **THEN** the system treats qty as 1 and selects the first unsold NIK

### Requirement: VND to xu conversion and trade collection
The system SHALL convert `totalVND` to `expectedXu` as `ceil(totalVND / vnd_per_xu)` using a global `vnd_per_xu` rate (default 1000 if not configured), quote the price in private chat (showing both VND and xu), invite trade, and validate the buyer's offered xu (`xuKhach >= expectedXu`) before confirming. On success the trade is confirmed; on shortfall it is cancelled with a message stating the required amount.

#### Scenario: Price quote shows VND and xu
- **WHEN** `totalVND=220000` and `vnd_per_xu=1000`
- **THEN** the system sends a private message containing `220000 VND` and `220 xu` and an invite to trade

#### Scenario: Sufficient xu confirms trade
- **WHEN** `expectedXu=220` and buyer offers `xuKhach=220`
- **THEN** the system confirms the trade

#### Scenario: Insufficient xu cancels trade
- **WHEN** `expectedXu=220` and buyer offers `xuKhach=100`
- **THEN** the system cancels the trade and notifies the buyer of the shortfall

### Requirement: NIK delivery and sold transition
The system SHALL, upon successful trade completion, move the purchased NIKs from CHUA_BAN to DA_BAN (preserving their original order), set each `NguoiMua` to the buyer's name, persist `nik_ban.txt` immediately, append one line per sold NIK to `logs/nik_sold.log` (`timestamp | TK | VatPham | GiaVND | NguoiMua`), and deliver the credentials to the buyer via private chat containing `TK` and full `MK` for each NIK.

#### Scenario: Delivery after trade success
- **WHEN** trade for `mua nik 2` completes for buyer `playerA`
- **THEN** the two NIKs appear in DA_BAN with `NguoiMua=playerA`, the file is updated, the log is appended, and `playerA` receives private messages containing `TK` and full `MK` for both NIKs

#### Scenario: Persistence is immediate
- **WHEN** the system moves NIKs to DA_BAN
- **THEN** `nik_ban.txt` is rewritten before any further purchase is accepted

### Requirement: Chat routing between NIK and item sales
The system SHALL route incoming private messages containing the token `nik` (case-insensitive, after trimming and lowercasing) to the NIK purchase flow; all other messages SHALL follow the existing item-sale flow. The routing SHALL occur before item parsing and SHALL not alter BanSeller item behavior.

#### Scenario: nik keyword routes to NIK flow
- **WHEN** buyer sends `mua nik 2`
- **THEN** the NIK flow is invoked and the item flow is not

#### Scenario: Non-nik message stays on item flow
- **WHEN** buyer sends `mua d7 5`
- **THEN** the item flow is invoked and the NIK flow is not

### Requirement: Busy guard for concurrent trades
The system SHALL reject a new `mua nik` request if either the item seller or the NIK seller is already in a non-IDLE trade state, replying that the bot is busy with another buyer.

#### Scenario: Busy during item trade
- **WHEN** BanSeller is in `STATE_IN_TRADE` and buyer sends `mua nik 1`
- **THEN** the system replies that the bot is busy and does not start a NIK trade
