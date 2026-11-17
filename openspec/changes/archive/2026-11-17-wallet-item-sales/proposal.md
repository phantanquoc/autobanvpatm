## Why

The shop currently sells items for in-game xu and also has an unfinished, uncommitted NIK (account) selling feature with per-order ATM payment. Per-order bank transfers make every purchase slow, and the NIK business is being discontinued. Customers should top up once into a VND wallet and then buy items instantly, with the bot auto-trading on wallet balance instead of xu.

## What Changes

- **BREAKING**: Remove all NIK selling: `NikSeller`, `NikStore`, `NikEntry`, `NikFrame`, the "Quan ly NICK" GUI button, all NIK routing/delegation in `BanSeller` (`nickOnly`, `[NICK]` config flag, `sendHelpNik`, ATM-NIK routing), and the uncommitted `openspec/specs/nik-*` specs. Archived NIK changes remain untouched as history.
- **BREAKING**: Item purchases no longer accept in-game xu. The price column in `BanVatPham/items/*.txt` is interpreted as direct VND (no conversion from old xu prices; the operator updates prices before go-live).
- New customer wallet (VND) shared across all shop bots: auto-created on first interaction, permanent deposit code (`VI` + 6 digits), first character name is the primary account.
- Wallet top-up via SePay webhook (salvaged from the uncommitted `AtmPaymentService`): transfer content containing a wallet deposit code credits that wallet; duplicate transaction ids are rejected via persisted seen-ids; unmatched content is logged only.
- Buy flow reuses the existing trade machinery: check available balance (balance minus holds) → place hold → prepare stock / storage withdraw (existing behavior) → trade invite (opcode 43) → bot offers items at 0 xu → buyer locks trade → bot confirms → trade complete (opcode 58) captures the hold; cancel/timeout (90s) releases the hold. Insufficient balance replies with the shortage plus bank account, deposit code, and VietQR.
- Wallet linking: primary character invites a secondary character via chat; the secondary confirms via chat within 15 minutes; only the primary can unlink. Any linked character may spend or transfer.
- Wallet transfer via chat to any game account (fee 0); recipient wallet is auto-created if none exists.
- No bank withdrawal, no automatic refunds, no wallet GUI — wallet balance/deposit info is available via simple in-game chat commands only.

## Capabilities

### New Capabilities
- `customer-wallet`: Wallet lifecycle, persistence, ledger, holds, linking, transfers, chat commands
- `wallet-topup-sepay`: SePay webhook crediting wallets by permanent deposit code, anti-replay, unmatched handling
- `item-sales-wallet-payment`: Item purchase flow paid from wallet (hold/capture/release) reusing existing stock/storage/trade machinery

### Modified Capabilities
- None (NIK capabilities are removed, not modified; their uncommitted specs are deleted rather than delta-specced)

## Impact

- Code: `BanVatPham/patcher/BanSeller.java` (purchase flow, help/broadcast in VND, remove NIK), `BanVatPham/patcher/Class_cw_edit.java` (remove NICK button), `BanVatPham/patcher/AtmPaymentService.java` (callback retargeted to wallet service), new wallet classes in `BanVatPham/patcher/`; deleted: `NikSeller.java`, `NikStore.java`, `NikEntry.java`, `NikFrame.java`, `AtmOrder.java` (superseded).
- Config: `BanVatPham/banvatpham.txt` ATM tags (`ATM`, `BANK_CODE`, `BANK_STK`, `BANK_NAME`, `SEPAY_SECRET`, `ATM_PORT`); `BanVatPham/items/*.txt` prices become VND (operator-edited).
- Data: new wallet file + append-only ledger under the bot working dir (`logs/`); persisted `atm_seen.log` anti-replay.
- Build: jar rebuilt via the existing project pipeline; bots must boot with `ATM=0` and `ATM=1`.
