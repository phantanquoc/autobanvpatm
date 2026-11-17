## 1. WalletStore admin support

- [x] 1.1 Add synchronized snapshot read APIs to `WalletStore`: `listWallets()` (defensive copy), `holdsOf(code)`, and total-held computation, without exposing live collections
- [x] 1.2 Add `adminAdjust(code, delta, reason)`: validate wallet exists, delta != 0, reason non-empty; debit limited to available balance (balance − holds); mutate + `save()` + ledger `ADMIN_CREDIT`/`ADMIN_DEBIT` with reason; return error string or null ← (verify: debit covering an active hold is rejected; credit persists across a store reload)

## 2. Live price reload in BanSeller

- [x] 2.1 Track `currentItemFile` when `ensureConfigLoaded` resolves the bot's `[ITEM]` file; make `priceList` swaps atomic (replace reference, synchronized access where read)
- [x] 2.2 Add `reloadConfig()`: re-read `currentItemFile`, swap price list, reset `broadcastReady`; add `pendingReload` flag consumed in `reset()` when a reload was requested mid-trade; bots in `loadFromPriceList` fallback report not-reloadable ← (verify: reload while IDLE changes next quote; reload requested mid-trade leaves in-flight total/hold untouched and applies after reset)

## 3. Admin web server

- [x] 3.1 Add `WalletService` helpers for `ADMIN_PORT` (default 8090) and `ADMIN_PASS` tags; create `AdminWebServer` singleton mirroring `AtmPaymentService` (JDK HttpServer, daemon pool) that starts only when the password is non-empty; start it once from the `BanSeller` constructor beside the ATM startup guard
- [x] 3.2 Implement auth: login form, constant-time password compare, in-memory sessions (random token, `HttpOnly` cookie, 8h expiry, lazy purge), CSRF token per session required on all POSTs, HTML escaping helper, failed-login ledger line `ADMIN_LOGIN_FAIL` ← (verify: no session → redirect to login; wrong password → no session + logged; POST without CSRF → 403 and no mutation)
- [x] 3.3 Dashboard + log pages: wallet count, total balance, total held, today's `CREDIT` total from `logs/wallet_ledger.log`, bot runtime statuses, webhook running flag; tail views of `wallet_ledger.log` and `atm_webhook.log`
- [x] 3.4 Wallet pages: search by character/code, detail page (balance, available, primary, linked, active holds, ledger lines for that wallet), adjust form (amount + direction + mandatory reason) wired to `adminAdjust`, unlink action via `WalletStore.unlink(primary, target)` ← (verify: end-to-end credit shows in ledger view and in-game `vi`; overspend debit rejected with error shown)
- [x] 3.5 Bot config pages: list lines from `banvatpham.txt` joined with runtime status via `Class_cu.a().d` + `banSeller` state/stock; add/edit/delete forms with required-field and duplicate SV+TK validation, atomic write with `banvatpham.txt.bak-<timestamp>` backup, auto-create missing `items/<name>.txt`, and a visible "applies after restart" notice
- [x] 3.6 Items pages: edit table for any `items/*.txt` (id, VND price, maxQty, code; add/remove rows) with full-row validation (unique non-empty codes), atomic write + backup, then trigger reload on every running bot using that file via the BanSeller mechanism in group 2 ← (verify: price saved on web changes the next in-game quote with no restart, and broadcasts quote the new price)

## 4. Build

- [x] 4.1 Compile all patcher sources against the current jar classpath with no errors; rebuild `ninja_ban_new.jar` via the existing project pipeline, keeping a timestamped backup of the previous jar ← (verify: jar contains `AdminWebServer` classes and no stale references; bot boots from the new jar)

## 5. Manual verification

Note (post-verify fixes): W1-W5 from verification fixed - items-save flash now reports skipped-no-item-file bots; NV/CHAT/NICK validated and unknown bot-line tags preserved on edit; items file created before bot config write (config unchanged on failure); runtime join requires determinable server match and case-insensitive TK; empty price reload clears stale broadcast lines. Verified via rebuild + tmp_wd_test/AdminHarness.java pass.

- [ ] 5.1 (partial harness check done: admin server starts/logs on custom port with password; no-ADMIN_PASS game boot not run - needs live game env) Boot checks: bot starts cleanly with no `ADMIN_PASS` (no listener on 8090) and with `ADMIN_PASS` set (log line `Admin web started on port 8090`)
- [ ] 5.2 (partial curl/harness pass done via tmp_wd_test/AdminHarness.java: auth/CSRF/dashboard/wallet adjust+hold-debit rejection/bot add+duplicate+backup/items validation+save/logs; in-game live price, mid-trade deferral, browser and unlink UI flow not run - needs live game env) Browser/curl pass: login/logout, wrong password, CSRF rejection, dashboard numbers match files, wallet credit/debit/unlink, add-bot flow writes line + backup, price edit applies live, mid-trade deferral, log views render ← (verify: every scenario in `specs/web-admin/spec.md` passes)
- [ ] 5.3 (not run - needs live game/SePay env) Regression pass: SePay webhook curl (credited/duplicate/401/unmatched), wallet buy sufficient/insufficient/cancel-release behave as before with the new jar
