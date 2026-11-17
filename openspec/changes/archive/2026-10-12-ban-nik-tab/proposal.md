## Why

The bot currently only sells in-game items for xu (BanSeller). Operators need to sell **game accounts (NIK)** — each NIK has credentials, a description, and a VND price. There is no UI or trade flow for NIK inventory, and the "Tong Xu" box occupies the only status area that could host it. Phase 1 delivers NIK sales via the existing xu-trade mechanism (VND price converted to xu via a rate); Phase 2 will replace the xu check with ATM bank-transfer verification without changing the tab structure.

## What Changes

- Add NIK domain model and persistence: `NikEntry` + `NikStore` reading/writing `workDir/nik_ban.txt` (`TK:MK:VatPham:GiaVND:TrangThai:NguoiMua`) and appending `logs/nik_sold.log` on each sale.
- Add `NikSeller` state machine for the `mua nik <qty>` flow: FIFO selection of the first N unsold entries, totalVND computation, VND->xu conversion via `vnd_per_xu` config, price quote, trade invite, and post-trade delivery (private chat with full TK/MK + move to sold table).
- Add UI in `Class_cw`: replace the single `xuBox` (JTextPane showing Tong Xu) with a tab switcher (`[Vat Pham]` / `[Nik]` via CardLayout); keep Tong Xu inside the Vat Pham tab; the Nik tab shows two JT λόγ: CHUA BAN (`Tai khoan | Mat khau*** | Vat pham | Gia VND`) and DA BAN (`Tai khoan | Mat khau | Vat pham | Nguoi mua | Gia`) plus buttons Them/Xoa/Sua/Hoan tac.
- Route private messages in `BanSeller.onPrivateMessage`: if content contains `nik` delegate to `NikSeller`, otherwise keep existing BanSeller item flow. Do not modify BanSeller item logic.
- Introduce `vnd_per_xu` config and `nik_ban.txt` lifecycle (load on startup, save on every mutation).

## Capabilities

### New Capabilities
- `nik-sales`: NIK inventory, FIFO purchase flow, VND->xu trade, delivery, logging, and Nik tab UI.
- `nik-admin-ui`: Nik tab layout, tab switching, table masking, and admin actions (add/edit/delete/undo).

### Modified Capabilities
- (none — BanSeller behavior unchanged; routing is additive)

## Impact

- Affected code: `BanVatPham/patcher/Class_cw_edit.java` (frame layout), `BanVatPham/patcher/BanSeller.java` (message routing), new files `NikSeller.java`, `NikEntry.java`, `NikStore.java`, plus config/file handling for `nik_ban.txt`.
- No new external dependencies; no API calls in Phase 1.
- Risk: Swing threading for table updates; file I/O failures must not crash bot — degrade to in-memory state with error log.
