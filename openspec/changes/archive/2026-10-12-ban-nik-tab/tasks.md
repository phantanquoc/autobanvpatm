## 1. Domain model and persistence

- [ ] 1.1 Create `NikEntry` POJO with fields `tk, mk, vatPham, giaVnd, trangThai, nguoiMua` and helpers for `isSold()` and `toLine()`/`fromLine()`
- [ ] 1.2 Create `NikStore` with `load(workDir)` and `save(workDir)` for `nik_ban.txt` (`TK:MK:VatPham:GiaVND:TrangThai:NguoiMua`), synchronized, skipping malformed lines with log, preserving order
- [ ] 1.3 Add `vnd_per_xu` config resolution (read `VND_PER_XU` from `banvatpham.txt` or fallback `config.txt`, default `1000`) and `logs/nik_sold.log` append helper (`timestamp | TK | VatPham | GiaVND | NguoiMua`)
- [ ] 1.4 Add input validation for add/edit (reject empty, `:` and newline in any field, non-positive or non-integer GiaVND) with error dialog path

## 2. NIK trade flow (NikSeller)

- [ ] 2.1 Create `NikSeller` class holding `Class_cd cd`, `NikStore store`, trade state (`IDLE/WAITING/IN_TRADE/CONFIRMED`), and `expectedXu/totalVND/buyerName/pendingEntries`; wire `BanSeller.onPrivateMessage` to route messages containing token `nik` to `NikSeller.handlePrivateMessage`, otherwise keep BanSeller path
- [ ] 2.2 Implement FIFO selection for `mua nik <qty>` (default 1, case-insensitive, trimmed/lowercased): validate qty, check `available < qty` -> reply, compute `totalVND` and `expectedXu = ceil(totalVND / vnd_per_xu)`, send price quote (VND + xu) and invite trade
- [ ] 2.3 Implement trade invite and xu validation (`sendInviteOpcode43`-style via `cd.l`, handle `onTradeOffer(xuKhach)` confirming if `xuKhach >= expectedXu` else cancelling with shortfall message; expose `isPaymentValid` seam for Phase 2)
- [ ] 2.4 Implement delivery on `onTradeComplete`: move purchased entries CHUA_BAN -> DA_BAN (set `nguoiMua`), call `NikStore.save()` synchronously, append `nik_sold.log`, send private chat(s) with full `TK/MK` to buyer, and post table refresh to EDT
- [ ] 2.5 Add busy guard rejecting new `mua nik` if `BanSeller.state != IDLE` or `NikSeller.state != IDLE` with "bot dang ban" reply; add trade-window lifecycle (`onTradeWindowOpened`/`onTradeCancelled`/`reset`) for NIK state
- [ ] 2.6 Add `NikSeller` trade-empty offer (seller offers `new Class_al[0]` with `xu=0`; verify server accepts empty offer and document fallback if not) ← (verify: end-to-end `mua nik 2` with 3 fake NIKs completes trade, moves rows, delivers TK/MK, and persists file)

## 3. Admin UI — Nik tab in Class_cw

- [ ] 3.1 Replace `xuBox` area with CardLayout: create `cards` panel (CardLayout) containing `cardVatPham` (original bot table + Tong Xu JTextPane) and `cardNik`; add top `tabBar` with `[Vat Pham]` / `[Nik]` buttons that call `cards.show(...)`; keep `findTextPane` fallback so failure degrades to original rendering
- [ ] 3.2 Build CHUA BAN JTable (`Tai khoan | Mat khau | Vat pham | Gia VND`) backed by `NikStore` CHUA_BAN list, with Mat khau rendered as `******` (real value retained in backing list via custom renderer or masked `getValueAt`)
- [ ] 3.3 Build DA BAN JTable (`Tai khoan | Mat khau | Vat pham | Nguoi mua | Gia`) showing real MK and buyer
- [ ] 3.4 Add admin buttons `Them`/`Xoa`/`Sua`/`Hoan tac` with dialogs, confirmation for Xoa, synchronized `NikStore` mutations, immediate `save()` and EDT table refresh for each action; `Hoan tac` moves selected DA_BAN row back to end of CHUA_BAN clearing `nguoiMua`
- [ ] 3.5 Add empty-state labels and non-blocking error dialogs for load/save failures (keep in-memory state, do not crash main window); wire initial `NikStore.load()` on first Nik tab show and refresh on every mutation ← (verify: tab switches preserve Tong Xu in Vat Pham card, CHUA/DA BAN tables render/mask correctly, all four admin actions persist and survive reload, undo works, empty and I/O-error states handled)
