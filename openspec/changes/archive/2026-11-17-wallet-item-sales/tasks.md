## 1. Remove NIK selling

- [x] 1.1 Delete `BanVatPham/patcher/NikSeller.java`, `NikStore.java`, `NikEntry.java`, `NikFrame.java` and `AtmOrder.java`
- [x] 1.2 Remove all NIK wiring from `BanSeller.java`: `nikSeller`/`sharedNikStore`/`getSharedNikStore`/`getSharedNikSeller`, `nickOnly` + `[NICK]` parsing, `isAtmNikRouting`, `sendHelpNik`, and NIK delegates in `onPrivateMessage`/`onTradeWindowOpened`/`onTradeOffer`/`onTradeComplete`/`onTradeCancelled`
- [x] 1.3 Remove the "Quan ly NICK" button (`ensureNikButton`) from `Class_cw_edit.java`
- [x] 1.4 Delete uncommitted `openspec/specs/nik-admin-ui/`, `openspec/specs/nik-atm-payment/`, `openspec/specs/nik-sales/` (keep archived changes and jar backups untouched) ← (verify: no remaining references to Nik* classes or nick routing compile or exist in source)

## 2. Wallet core

- [x] 2.1 Create wallet model + `WalletStore` in `BanVatPham/patcher/`: wallet (deposit code `VI`+6 unique digits, primary name, linked names, balance), static shared singleton synchronized on the store, load/save from working dir with temp-file-then-rename
- [x] 2.2 Implement append-only ledger under `logs/` for credit/hold/capture/release/transfer/link/unlink, written on every mutation
- [x] 2.3 Implement holds: in-memory hold map keyed by wallet+trade, available = balance − holds; place/capture/release operations atomic under the store lock ← (verify: concurrent holds across two BanSeller instances cannot overspend; capture/release math matches spec scenarios)

## 3. Wallet chat commands

- [x] 3.1 Auto-create wallet on first wallet/purchase interaction (creator = primary), wire wallet commands into `BanSeller.onPrivateMessage`: view wallet, deposit info (bank, code, VietQR)
- [x] 3.2 Implement linking: primary invites by chat, secondary confirms by chat within 15 minutes (pending-invite expiry), primary-only unlink
- [x] 3.3 Implement transfer command: positive amount, zero fee, available-balance check, auto-create recipient wallet, private confirmations to both parties ← (verify: link expiry, non-primary unlink refusal, transfer to wallet-less account, and overspend refusal all behave per spec)

## 4. SePay top-up

- [x] 4.1 Retarget `AtmPaymentService` callback from `NikSeller` to the wallet service; keep secret verification, SePay field-variant parsing, port from `ATM_PORT` (default 8089), start only when `[ATM]=1`
- [x] 4.2 Implement crediting: content contains deposit code (case-insensitive) → credit full amount, persist seen transaction id (`atm_seen.log`), ledger line, notify customer if online; unmatched content → log only; replayed id → duplicate, no double credit
- [x] 4.3 Read ATM config tags (`ATM`, `BANK_CODE`, `BANK_STK`, `BANK_NAME`, `SEPAY_SECRET`, `ATM_PORT`) from `banvatpham.txt` in the wallet/BanSeller scope (replacing the helpers that lived in `NikSeller`) ← (verify: valid webhook credits once, replay rejected after restart, wrong secret 401, unmatched logged without credit)

## 5. Wallet purchase flow

- [x] 5.1 Reprice: interpret `items/*.txt` price column as direct VND; update help/broadcast to quote VND (no xu wording, no VND_PER_XU conversion)
- [x] 5.2 In `BanSeller` purchase flow: after stock fulfillable check, check available balance — insufficient → reply shortage + deposit instructions, no hold; sufficient → place hold, then existing prepareStock/invite (opcode 43)/offer-at-0-xu flow
- [x] 5.3 Change trade confirmation: buyer locks trade (case 45) on a wallet-paid order → bot confirms regardless of xu; on trade complete (case 58) capture the hold (ledger-log capture intent before confirming), on cancel (case 57)/90s timeout/buyer-not-found/prepare failure release the hold ← (verify: end-to-end — sufficient buy captures exactly once, cancel/timeout releases and funds are immediately reusable, xu offered outside wallet flow is still rejected)

## 6. Build and manual verification

- [x] 6.1 Rebuild the jar via the existing project pipeline; confirm no compile errors and no dangling Nik*/AtmOrder references
- [ ] 6.2 Boot checks: bot starts cleanly with `ATM=0` (no webhook) and `ATM=1` (webhook listening)
- [ ] 6.3 Manual test pass: webhook credit valid/replay/unmatched, buy sufficient/insufficient, trade cancel release, two-bot concurrent buy, link secondary, transfer, restart preserves balances ← (verify: every spec scenario in customer-wallet, wallet-topup-sepay, and item-sales-wallet-payment passes)
