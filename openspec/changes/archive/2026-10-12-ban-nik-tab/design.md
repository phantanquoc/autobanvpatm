## Context

The bot is a Java Swing desktop app (NSO auto buy/sell). `BanSeller` handles item sales: `onPrivateMessage` parses `mua/ban <mavp> <qty>`, quotes `donGia * qty` in xu, invites trade (opcode 43), and validates `xuKhach >= expectedXu` in `onTradeOffer`. `Class_cw` (ActionListener on a timer / Swing Timer) renders the bot table and a single `JTextPane xuBox` ("Tong Xu" per server) found via `findTextPane`. Config lives in `workDir/banvatpham.txt` + `items/*.txt`. There is no NIK concept yet; no file, no UI, no trade path for accounts.

Stakeholders: bot operator (admin UI), in-game buyer (private chat + trade). Constraint: Swing is single-threaded; `Class_cw.actionPerformed` is called on EDT / timer thread; file I/O must not block it for long.

## Goals / Non-Goals

**Goals:**
- Sell NIKs (game accounts) from a list managed in-app, FIFO by `mua nik <qty>`.
- Show NIK inventory in a new tab that replaces the Tong Xu box area with a `[Vat Pham] | [Nik]` switcher; keep Tong Xu visible in the Vat Pham tab.
- Persist NIKs in `workDir/nik_ban.txt` and log sales to `logs/nik_sold.log`.
- Phase 1: collect xu via the existing trade window (VND price converted via `vnd_per_xu`); deliver TK/MK via private chat; move entries from CHUA BAN to DA BAN with undo.

**Non-Goals:**
- ATM/bank polling (Phase 2 — only a seam is prepared).
- Verifying that the NIK actually owns the described items.
- Excel import/export, multi-server NIK routing, or encryption of `nik_ban.txt`.

## Decisions

### 1. Separate NikSeller / NikStore / NikEntry (vs extending BanSeller)
- **Choice:** New classes `NikEntry` (POJO), `NikStore` (file I/O + in-memory lists), `NikSeller` (chat + trade flow). `BanSeller.onPrivateMessage` only routes: if normalized content contains token `nik` -> `NikSeller.handle(...)`, else BanSeller.
- **Rationale:** BanSeller's state machine is tied to `orderItemId/tradeSlots/prepareStock/selectWholeSlots` — none apply to NIKs. Sharing state would create cross-contamination bugs. Isolation keeps BanSeller diff near-zero.
- **Alternative:** Add `mode=NIK` inside BanSeller — rejected (bloats existing state machine, harder to test).

### 2. File format: single `nik_ban.txt` with `TK:MK:VatPham:GiaVND:TrangThai:NguoiMua`
- **Rationale:** One file is atomic for load/save; operator can hand-edit; `:` delimiter matches `banvatpham.txt` style. `TrangThai` is `0=CHUA_BAN,1=DA_BAN`. Empty `NguoiMua` for unsold.
- **Escape:** Values may not contain `:` or newline; validated on save (reject with dialog). MK is stored plaintext in file (operator controls file access); UI masks it.
- **Alternative:** Two files `nik_chuaban.txt` + `nik_daban.txt` — rejected (requires two-phase commit on sale).

### 3. Price model: VND per NIK + global `vnd_per_xu` rate
- **Rationale:** Requirement is VND-only pricing per NIK. Trade still collects xu, so `expectedXu = ceil(totalVND / vnd_per_xu)` (integer division with round-up to avoid under-charge). `vnd_per_xu` defaults to `1000` if not configured; read from `banvatpham.txt` key `VND_PER_XU` or `config.txt`.
- **Alternative:** Dual pricing (VND + xu) — rejected as extra column with no use in Phase 1.

### 4. FIFO selection + `mua nik <qty>`
- **Rationale:** Simplest deterministic rule; operator orders the file top-to-bottom as display order. Parse: `mua nik 3` -> qty=3; `mua nik` (no qty) -> qty=1; non-integer / qty<=0 -> error message.
- **Stock check:** If `available < qty` -> reply "Chi con N nik chua ban".

### 5. Trade for NIK: empty item offer
- **Rationale:** NIK delivery is out-of-band (private chat). Trade is only to collect xu. Implementation: `NikSeller` calls `cd.l.a(0, new Class_al[0])` (or empty array) to lock trade with `xu=0` on seller side; buyer's `xuKhach` is validated against `expectedXu` exactly like BanSeller's `onTradeOffer`. No `prepareStock` needed.
- **Phase 2 seam:** Extract `NikSeller.isPaymentValid(int xuKhach)` / `verifyPayment()` so Phase 2 can swap xu check for bank polling without touching the rest.

### 6. UI: CardLayout switcher replacing xuBox area
- **Choice:** In `Class_cw`, keep reference to the container that held `xuBox`. Replace `findTextPane` + `setText` path with a `JPanel cards` (CardLayout) holding `cardVatPham` (original xuBox) and `cardNik` (two JTales + buttons). Top of `cards` has a `JPanel tabBar` with two `JButton`s `[Vat Pham]` / `[Nik]` that call `cards.show(...)`.
- **Rationale:** Minimal layout surgery; Tong Xu not lost. Uses existing Swing idioms in the codebase (JTable + DefaultTableModel already used for bot table).
- **Table masking:** CHUA BAN model stores real MK but renders `******` via custom `TableCellRenderer` (or by returning masked string from `getValueAt` and keeping real value in backing list). DA BAN shows real MK.

### 7. Threading
- **Choice:** All Swing mutations via `SwingUtilities.invokeLater`. `NikStore.save()` is synchronized; `actionPerformed` triggers `NikStore.load()` once on first show and reuses in-memory list thereafter. Trade callbacks may arrive off-EDT, so `NikSeller.onTradeComplete` posts table refresh to EDT.

## Risks / Trade-offs

- **Swing EDT blocking on file I/O** -> Mitigation: `nik_ban.txt` is small (hundreds of lines); I/O is <10ms. Still wrap in try/catch; on failure show dialog and keep in-memory state.
- **`:` in TK/MK/VatPham breaks parsing** -> Mitigation: validate on add/edit, reject with message; on load, lines that fail to parse are skipped with a log line.
- **Concurrent buy while admin edits** -> Mitigation: `NikStore` methods are `synchronized`; admin actions and `NikSeller` trade completion both synchronize on the store.
- **Trade invite collision with BanSeller trade** -> Mitigation: route is at chat level; trade-level collision means at most one `STATE_IN_TRADE` at a time — if `BanSeller.state != IDLE` or `NikSeller.state != IDLE`, the new `mua nik` is rejected with "Bot dang ban cho nguoi khac".
- **Empty trade rejected by server** -> Mitigation: test with empty `Class_al[0]`; if server requires at least one slot, fallback to offering a dummy 1-xu item or keep the trade logic server-agnostic and document fallback.

## Migration Plan

1. Add new files (`NikEntry`, `NikStore`, `NikSeller`) — no migration, feature-flagged by existence of `nik_ban.txt` (if missing, Nik tab shows empty state with "Chua co nik").
2. Patch `Class_cw_edit.java` to add CardLayout and Nik tables; guard with try/catch so failure falls back to original xuBox rendering.
3. Patch `BanSeller.onPrivateMessage` to add 3-line route to `NikSeller`; no change to item path.
4. Deploy; operator creates `nik_ban.txt` via UI (file auto-created). Rollback: delete `nik_ban.txt` handling still works; UI degrades to empty Nik tab.

## Open Questions

- None blocking Phase 1. Phase 2 will need: bank API shape, `transferContent` template, polling interval, and idempotency key.
