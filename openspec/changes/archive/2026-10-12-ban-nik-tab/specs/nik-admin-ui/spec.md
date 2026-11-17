## ADDED Requirements

### Requirement: Nik tab layout replacing Tong Xu box
The system SHALL replace the single `xuBox` (JTextPane showing Tong Xu) area in `Class_cw` with a tab switcher consisting of two buttons `[Vat Pham]` and `[Nik]` that toggle a CardLayout between `cardVatPham` (containing the original bot table and the Tong Xu JTextPane) and `cardNik` (containing the NIK inventory UI). Tong Xu SHALL remain visible only inside the Vat Pham card.

#### Scenario: Initial view shows Vat Pham
- **WHEN** the main window first renders
- **THEN** the Vat Pham card is visible and the Tong Xu box shows the per-server totals as before

#### Scenario: Switching to Nik tab
- **WHEN** the admin clicks `[Nik]`
- **THEN** the Nik card becomes visible showing two tables (CHUA BAN and DA BAN) and admin buttons, and the Vat Pham card is hidden

#### Scenario: Switching back to Vat Pham
- **WHEN** the admin clicks `[Vat Pham]` while the Nik card is visible
- **THEN** the Vat Pham card becomes visible again with the bot table and Tong Xu

### Requirement: CHUA BAN table with masked passwords
The system SHALL display the CHUA BAN table with columns `Tai khoan | Mat khau | Vat pham | Gia VND` where the Mat khau cell renders as `******` while the backing model retains the real password. The table SHALL reflect the order of `nik_ban.txt` CHUA_BAN entries top-to-bottom.

#### Scenario: Password is masked on screen
- **WHEN** a NIK with password `abc123` is in CHUA BAN
- **THEN** the table cell shows `******` and the real password remains in the backing store

#### Scenario: Order matches file order
- **WHEN** `nik_ban.txt` lists three CHUA_BAN NIKs in order A, B, C
- **THEN** the CHUA BAN table shows rows A, B, C in that order

### Requirement: DA BAN table with buyer column
The system SHALL display the DA BAN table with columns `Tai khoan | Mat khau | Vat pham | Nguoi mua | Gia` where Mat khau shows the real password and Nguoi mua shows the buyer name recorded at sale time.

#### Scenario: Sold entry shows buyer
- **WHEN** NIK `TK=s2huutho` was sold to `playerA`
- **THEN** the DA BAN table shows that row with `Nguoi mua = playerA`

### Requirement: Admin actions add, edit, delete, and undo
The system SHALL provide admin buttons `Them`, `Xoa`, `Sua`, and `Hoan tac` for the Nik tab. `Them` SHALL prompt for TK/MK/VatPham/GiaVND, validate that no field is empty, no field contains `:` or newline, and GiaVND is a positive integer before inserting at the end of CHUA_BAN. `Xoa` SHALL remove the selected CHUA_BAN row after confirmation. `Sua` SHALL allow editing the selected CHUA_BAN row with the same validation. `Hoan tac` SHALL move the selected DA_BAN row back to the end of CHUA_BAN (clearing NguoiMua and setting TrangThai to 0). Each mutation SHALL persist `nik_ban.txt` immediately and refresh both tables on the EDT.

#### Scenario: Add valid NIK
- **WHEN** admin submits TK=`acc1`, MK=`pass1`, VatPham=`Set full`, GiaVND=`50000`
- **THEN** a new row appears at the bottom of CHUA BAN and `nik_ban.txt` contains the new line

#### Scenario: Add rejects invalid GiaVND
- **WHEN** admin submits GiaVND=`abc` or empty TK
- **THEN** the system shows a validation error and does not insert a row

#### Scenario: Undo moves sold back to unsold
- **WHEN** admin selects a DA_BAN row and clicks `Hoan tac`
- **THEN** that row is removed from DA BAN and appended to CHUA BAN with `NguoiMua` cleared, and the file is updated

### Requirement: Empty states and error handling for Nik tab
The system SHALL show an empty-state message when CHUA BAN or DA BAN has no rows, and SHALL show a non-blocking error dialog (without crashing the bot) if `nik_ban.txt` cannot be read or written.

#### Scenario: Empty CHUA BAN shows placeholder
- **WHEN** `nik_ban.txt` has no CHUA_BAN entries
- **THEN** the CHUA BAN table shows no rows and a label indicates there are no NIKs for sale

#### Scenario: File write failure is surfaced
- **WHEN** saving `nik_ban.txt` throws an IOException
- **THEN** the system shows an error dialog, keeps the in-memory state, and does not crash the main window
