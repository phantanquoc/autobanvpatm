## Context

Each shop bot is a `Class_cd` character instance in one JVM; `Class_cp` creates one `BanSeller` per bot. `BanSeller` loads its price table once (`configLoaded` guard) from `items/<ITEM>.txt`, resolving `<ITEM>` from its own `[SV]`+`[TK]` line in `banvatpham.txt`. Wallets live in the static shared `WalletStore` singleton (all methods synchronized, persisted to `wallets.txt`, audited in `logs/wallet_ledger.log`). `AtmPaymentService` already runs a JDK `com.sun.net.httpserver` webhook in-process. There is no runtime registry of bots beyond `Class_cu.a().d` (list of `Class_cd`), and bot account creation/login lives in unpatched original-jar classes (`Class_cu`/`Class_cd` loading config at startup). The operator manages everything via files and a Swing GUI on the VPS.

## Goals / Non-Goals

**Goals:**
- Authenticated web admin in the same JVM: dashboard, wallet view/adjust/unlink, bot config CRUD, live price editing, log tails.
- Price edits take effect on running bots without restart; wallet changes go through the same synchronized store the bots use.
- Zero new dependencies and zero extra processes on the VPS.

**Non-Goals:**
- Starting/stopping/logging-in bots at runtime (bot config edits apply on next bot restart).
- Editing ATM/SePay config from the web; multi-user roles; HTTPS inside Java (reverse proxy's job).

## Decisions

1. **Embedded `AdminWebServer` singleton, separate port (default 8090)** via JDK `HttpServer`, mirroring `AtmPaymentService` (daemon cached thread pool). Started once from the `BanSeller` constructor next to the existing ATM startup, guarded by a static flag like `walletInitDone`. Alternative (standalone web app reading the files) rejected: it would race `WalletStore`'s in-memory state and could corrupt `wallets.txt`.
2. **Fail-closed gate**: server starts only when `[ADMIN_PASS]` in `banvatpham.txt` is non-empty; port from `[ADMIN_PORT]` (default 8090). Read through existing `WalletService.tagValue`/`readConfigContent` helpers.
3. **Auth model**: single operator password, constant-time comparison, random session token in an `HttpOnly` cookie (8h expiry, in-memory session map), per-session CSRF token required on every POST, all user-derived output HTML-escaped. Login failures are logged to `atm_webhook.log`-style admin log line in `logs/wallet_ledger.log` as `ADMIN_LOGIN_FAIL` (no password material). This is proportionate for a single-operator shop; the design doc and login page both state the port must be firewalled to the operator IP or put behind an HTTPS reverse proxy.
4. **Wallet operations reuse the store lock**: new `WalletStore.adminAdjust(code, delta, reason)` validates (wallet exists, delta != 0, debit limited by available balance so active holds stay covered), mutates, `save()`s, and ledgers `ADMIN_CREDIT`/`ADMIN_DEBIT` with the reason. Read APIs (`listWallets` snapshot copy, `holdsOf`, total held) are synchronized copies so HTTP threads never iterate live collections. Unlink reuses `WalletStore.unlink(primary, target)`.
5. **Bot config CRUD edits `banvatpham.txt` as data**: parse the same line format the runtime uses (`[SV][TK][MK][NV][MAP][POS][ITEM][CHAT]`), validate required fields and TK+SV uniqueness, write atomically (temp file then rename, like `WalletStore.save`) after copying a `banvatpham.txt.bak-<timestamp>` backup. Runtime status joins config lines to live bots via `Class_cu.a().d` (match `cd.b` == TK and server) and `cd.p.banSeller` state/stock. UI states plainly that add/edit/delete applies after restart. A new `[ITEM]` name with no file creates `items/<name>.txt` with the format header comment.
6. **Live price reload**: `BanSeller` remembers `currentItemFile` when `ensureConfigLoaded` resolves it. New `reloadConfig()` re-reads that file into `priceList` and resets `broadcastReady` so public broadcasts quote new prices. After an items file is saved (atomic write + backup), the admin iterates `Class_cu.a().d`, and for each `banSeller` using that file: if `state == STATE_IDLE`, reload immediately; otherwise set a `pendingReload` flag that `reset()` (trade end/cancel path) consumes. This prevents repricing an in-flight order whose hold was placed at the old price. Fallback-mode bots (`loadFromPriceList`, no item file) are skipped and reported as not reloadable.
7. **Server-rendered HTML embedded as Java strings**, one handler class, small hand-rolled helpers (query/form parsing with URL decoding, HTML escape, number formatting reusing `Class_bb.a(long)` style grouping). No templates, no static files, no JSON API — forms + redirects (Post/Redirect/Get) with a flash message query param.

## Risks / Trade-offs

- [Admin port exposed to internet with a weak password] → fail-closed without password, session + CSRF, docs/UI instruct firewall/reverse-proxy; login failures logged.
- [Price file edited while a bot is mid-trade] → per-bot deferral via `pendingReload` consumed in `reset()`.
- [Concurrent edit of `banvatpham.txt` by admin and by hand on VPS] → atomic rename writes + timestamped backup before every admin write; last writer wins is accepted.
- [HTTP threads touching game objects] → admin only reads bot state/stock through existing thread-safe-ish getters used by the Swing GUI (`Class_cw_edit` does the same) and mutates only `WalletStore` (synchronized) and config files; price reload is a volatile flag + reload on the bot's own state checks where feasible, with synchronized `priceList` swap (replace list reference instead of mutate-in-place).
- [Session map grows unbounded] → sessions carry expiry and are purged lazily on each login/request.

## Migration Plan

1. Add config tags `[ADMIN_PORT]8090[/ADMIN_PORT][ADMIN_PASS]<strong password>[/ADMIN_PASS]` to `banvatpham.txt` on the VPS; open port 8090 in the VPS firewall restricted to the operator IP (or front with HTTPS reverse proxy).
2. Rebuild `ninja_ban_new.jar`, deploy with `_swap_nik_jar.bat` (keeps previous jar backups for rollback).
3. Verify per tasks.md section 5; rollback = swap back the previous jar (admin is additive; wallet/ledger formats unchanged).
