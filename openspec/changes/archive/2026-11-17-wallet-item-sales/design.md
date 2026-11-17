## Context

`BanSeller` (one instance per bot character `Class_cd`) sells items via in-game trade, currently paid in xu: buyer chats `<qty> <maVp>`, bot checks stock (`checkFulfill`, storage withdraw), invites trade (opcode 43), offers items at 0 xu, and confirms when the buyer offers enough xu (case 45 → `onTradeOffer`). An uncommitted NIK feature added `NikSeller/NikStore/NikEntry/NikFrame`, a "Quan ly NICK" button in `Class_cw_edit`, NIK routing inside `BanSeller`, and a SePay ATM flow (`AtmPaymentService` webhook + `AtmOrder` + persisted `atm_seen.log` anti-replay) that delivers account credentials on payment. Multiple bots run in one JVM sharing static state (existing pattern: `BanSeller.sharedNikStore`).

The business is dropping NIK and xu. Payment moves to a shared VND wallet topped up via SePay.

## Goals / Non-Goals

**Goals:**
- Items-only selling paid from a shared VND wallet; trade auto-executes on balance, no xu involved.
- Wallet shared across all bots in the JVM, safe against concurrent spends (holds).
- Reuse existing stock/storage/trade machinery and the salvaged SePay webhook + anti-replay.
- Remove all NIK code, GUI, routing, and uncommitted NIK specs.

**Non-Goals:**
- Bank withdrawal from wallet, automatic refunds, wallet GUI, transfer fees.
- Converting old xu prices to VND (operator edits `items/*.txt`).
- Multi-process / multi-machine wallet sharing (single JVM, single working dir only).

## Decisions

1. **Wallet store as static shared singleton** (mirrors `sharedNikStore` pattern): one `WalletStore` instance shared by every `BanSeller`, all mutations synchronized on the store. Alternative (per-bot wallet files) rejected — the user requires one wallet usable at any bot.
2. **Persistence**: wallet file in the working dir (`Class_cu.g`), written temp-file-then-rename so a crash mid-write cannot corrupt balances; every mutation appends a line to an append-only ledger in `logs/` (credit/debit-capture/hold/release/transfer/link). Alternative (rewrite-only, no ledger) rejected — the bot holds customer money, disputes need an audit trail.
3. **Holds instead of immediate debit**: a purchase places a hold; capture only on trade-complete (opcode 58), release on cancel/timeout/failure. Available balance = balance − sum(holds). This makes concurrent purchases across bots unable to overspend, and failed trades never need refunds. Alternative (debit upfront + refund on failure) rejected — refund paths are exactly what the user excluded.
4. **Permanent deposit code per wallet** (`VI` + 6 digits, unique): top-up webhook matches transfer content containing the code and credits that wallet. Replaces per-order `AtmOrder` codes — no order expiry for deposits, and money arriving when the customer is offline is still usable later. `AtmOrder` is deleted; `AtmPaymentService` is kept with its callback retargeted from `NikSeller` to the wallet service. Anti-replay via persisted seen transaction ids (`atm_seen.log`) is kept unchanged.
5. **Trade confirmation condition changes, machinery does not**: `onTradeOffer` currently confirms when `xuKhach >= expectedXu`. With wallet payment the bot offers items at 0 xu, the buyer locks the trade (case 45 with 0 xu), and the bot confirms because the order is wallet-paid (hold exists). Timeout (90 s), cancel (case 57), complete (case 58), `prepareStock`, storage withdraw, and broadcast/help flows stay; help/broadcast quote VND prices.
6. **Wallet identity = game character names**: the game server authenticates who chats, so any linked character name may spend/transfer. First character to interact creates the wallet and is primary; linking is a two-step chat handshake (primary invites, secondary confirms, 15-min expiry, mirroring the existing order-expiry pattern); only the primary unlinks.
7. **NIK removal is deletion, not feature-flagging**: delete `NikSeller/NikStore/NikEntry/NikFrame.java`, the `Class_cw_edit` NICK button, all `BanSeller` NIK branches, and uncommitted `openspec/specs/nik-*`. Archived NIK changes stay as history. Jar backups are not touched.

## Risks / Trade-offs

- [Wallet file corruption / crash] → temp-then-rename writes + append-only ledger allows rebuilding balances.
- [Two bots spending the same balance concurrently] → all balance mutations synchronized on the single shared store; holds reserve funds before any trade starts.
- [Deposit code shared/leaked] → a leaked code only lets others deposit *into* the wallet; spending still requires chatting from a linked game character.
- [Customer pays, never returns] → balance simply stays in the wallet; no undelivered-goods state exists (explicitly accepted by the user).
- [Trade succeeds in game but bot crashes before capture] → hold is in-memory only; on restart the wallet balance is unchanged while items are gone. Mitigation: capture intent is ledger-logged before confirming the trade, and the ledger is the source for manual reconciliation. Accepted trade-off for this scale; no auto-refund exists by design.
- [Old xu prices read as VND] → operator must edit `items/*.txt` before enabling; code treats column 2 as VND unconditionally.

## Migration Plan

1. Implement behind the existing `[ATM]` config tag: bots with `ATM=0` keep running with wallet purchases disabled (buy replies direct customers to top up / feature off message as specced).
2. Operator fills ATM bank tags in `banvatpham.txt` and rewrites `items/*.txt` prices in VND.
3. Rebuild jar via the existing pipeline (`NinjaBan.bat` / patcher build), deploy, run manual checks (webhook credit valid/replay/unmatched, sufficient/insufficient buy, cancel release, concurrent buys, link, transfer, restart persistence).
4. Rollback: restore previous jar backup; wallet file/ledger are additive and ignored by the old jar.
