## Why

The shop bots run on a rented VPS and are managed entirely through text files (`banvatpham.txt`, `items/*.txt`, `wallets.txt`, `logs/`) and a Swing GUI on the VPS desktop. The operator must remote into the VPS for every price change, bot addition, or wallet support case. A web admin embedded in the bot JVM gives remote management with zero extra infrastructure, reusing the existing embedded HTTP server pattern and the shared in-JVM wallet store.

## What Changes

- New embedded web admin server (`AdminWebServer`) in the bot JVM, on its own port (default 8090), started only when an admin password is configured (fail-closed).
- Password login with session cookie and CSRF tokens on all mutating requests; all rendered data HTML-escaped.
- Dashboard: wallet count, total balances, active holds, today's top-ups (from ledger), bot/webhook status.
- Wallet management: search wallets by character name or deposit code, view wallet detail (balance, available, holds, linked characters, ledger history), manual credit/debit with a mandatory reason recorded in the ledger, unlink a linked character.
- Bot management: list seller bots from `banvatpham.txt` with live runtime status (online/trade state/stock), add/edit/delete bot config lines with validation and atomic file writes plus timestamped backups. Bot config changes take effect after bot restart (stated in the UI).
- Price management: edit `items/*.txt` price tables (item id, VND price, max qty, item code) from the web; edits are saved atomically and applied live to running bots using that file without restart, except bots mid-trade are reloaded after their trade finishes.
- Log viewer: tail of `logs/wallet_ledger.log` and `logs/atm_webhook.log`.
- New admin config tags in `banvatpham.txt`: `ADMIN_PORT`, `ADMIN_PASS`.

## Capabilities

### New Capabilities
- `web-admin`: Embedded authenticated web admin — dashboard, wallet management (manual adjust, unlink), bot config management (add/edit/delete, restart-effective), live price management, log viewing

### Modified Capabilities
- None (wallet, top-up, and sales behavior toward customers is unchanged; the admin reuses existing wallet/trade machinery)

## Impact

- Code: new `BanVatPham/patcher/AdminWebServer.java`; additions to `BanVatPham/patcher/WalletStore.java` (admin adjust + wallet/hold snapshots), `BanVatPham/patcher/BanSeller.java` (`currentItemFile`, `reloadConfig()`, admin server startup next to ATM startup), `BanVatPham/patcher/WalletService.java` (admin config tag helpers).
- Config: `banvatpham.txt` gains optional `ADMIN_PORT`/`ADMIN_PASS` tags; `items/*.txt` and bot lines may be rewritten via the admin (atomic writes + `.bak-<timestamp>` backups).
- Runtime: new listening port 8090 only when `ADMIN_PASS` is set; no new dependencies (JDK `com.sun.net.httpserver`, same as `AtmPaymentService`); no changes to game protocol, wallet file format, ledger format (new `ADMIN_CREDIT`/`ADMIN_DEBIT` ledger types are additive), or SePay webhook behavior.
- Build: patcher sources rebuilt into `ninja_ban_new.jar` via the existing project pipeline; the running jar is replaced via `_swap_nik_jar.bat`.
