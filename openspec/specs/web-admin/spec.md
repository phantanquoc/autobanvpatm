## ADDED Requirements

### Requirement: Admin server gating and authentication
The system SHALL run an embedded web admin server on the configured `ADMIN_PORT` (default 8090) only when the `ADMIN_PASS` tag in `banvatpham.txt` is non-empty. Every admin page and action SHALL require a valid login session (random token in an `HttpOnly` cookie, 8-hour expiry). Every mutating request (POST) SHALL require the session's CSRF token. All user-derived data rendered in pages SHALL be HTML-escaped. Failed logins SHALL be rejected and logged without recording the attempted password.

#### Scenario: Admin disabled without password
- **WHEN** the bot starts and `ADMIN_PASS` is empty or absent
- **THEN** no admin server is started and no admin port is listening

#### Scenario: Unauthenticated access redirected
- **WHEN** a request without a valid session cookie hits any `/admin` page other than the login page
- **THEN** the response redirects to the login page and no shop data is rendered

#### Scenario: Wrong password rejected
- **WHEN** a login is submitted with an incorrect password
- **THEN** login fails with an error, no session is created, and the failure is logged

#### Scenario: POST without CSRF token rejected
- **WHEN** an authenticated POST request omits or mismatches the CSRF token
- **THEN** the request is rejected with HTTP 403 and no state changes

### Requirement: Dashboard
The system SHALL provide an admin dashboard showing: number of wallets, total wallet balances, total amount currently held, today's credited top-up total derived from the ledger, each configured bot's runtime status, and whether the SePay webhook is running.

#### Scenario: Dashboard reflects live state
- **WHEN** the operator opens the dashboard after customers have topped up and a purchase hold is active
- **THEN** wallet count, totals, held amount, and today's top-ups match `wallets.txt` and `logs/wallet_ledger.log`

### Requirement: Wallet viewing and manual adjustment
The system SHALL let the operator search wallets by character name or deposit code and view a wallet's balance, available balance, primary character, linked characters, active holds, and ledger history. The operator SHALL be able to credit or debit a wallet by an explicit amount with a mandatory reason; a debit SHALL NOT exceed the wallet's available balance (balance minus active holds) and SHALL NOT make the balance negative. Every adjustment SHALL be persisted and recorded in the ledger as `ADMIN_CREDIT` or `ADMIN_DEBIT` including the reason.

#### Scenario: Manual credit
- **WHEN** the operator credits 200000 VND to a wallet with reason "test nap"
- **THEN** the wallet balance increases by 200000, the change survives restart, and the ledger contains an `ADMIN_CREDIT` line with the reason

#### Scenario: Debit beyond available rejected
- **WHEN** the operator tries to debit more than the wallet's available balance (including amounts covered by an active purchase hold)
- **THEN** the adjustment is rejected with an error and neither balance nor ledger changes

#### Scenario: Unlink from admin
- **WHEN** the operator unlinks a linked character from a wallet in the admin
- **THEN** that character no longer belongs to the wallet and an `UNLINK` ledger line is written

### Requirement: Bot configuration management
The system SHALL list every bot line in `banvatpham.txt` (server, account, map, position, item file, chat file) joined with live runtime status when the bot is running (online state, trade state, stock summary). The operator SHALL be able to add, edit, and delete bot lines; writes SHALL validate required fields and reject a duplicate server+account pair, SHALL be written atomically, and SHALL first save a timestamped backup of `banvatpham.txt`. Adding a bot whose item file does not exist SHALL create `items/<name>.txt` with the standard format header. The UI SHALL state that bot changes take effect after the bot process restarts.

#### Scenario: Add bot
- **WHEN** the operator adds a valid new bot line
- **THEN** `banvatpham.txt` gains the line, a `banvatpham.txt.bak-<timestamp>` backup of the previous content exists, and the new bot appears in the list marked as requiring restart

#### Scenario: Duplicate bot rejected
- **WHEN** the operator adds a bot whose server+account pair already exists
- **THEN** the write is rejected and `banvatpham.txt` is unchanged

#### Scenario: Delete bot keeps backup
- **WHEN** the operator deletes a bot line
- **THEN** the line is removed from `banvatpham.txt` and the pre-delete backup file exists

### Requirement: Live price management
The system SHALL let the operator edit any `items/*.txt` price table (item id, VND price, max quantity, item code; add and remove rows). Saving SHALL validate every row (positive id, non-negative price, positive max quantity, non-empty unique item code), write the file atomically with a timestamped backup, and apply the new prices to running bots using that file without restart. A bot that is mid-trade SHALL apply the new prices only after its current trade ends or is cancelled; its in-flight order SHALL keep the price and hold amount from order time. Broadcast price quotes SHALL use the new prices after reload.

#### Scenario: Price change applies live
- **WHEN** the operator changes the price of item code `d7` and saves while the bot is idle
- **THEN** the next purchase quote for `d7` uses the new VND price with no bot restart

#### Scenario: Mid-trade bot defers reload
- **WHEN** prices are saved while a bot is in an active trade
- **THEN** the active order completes at its original total and hold amount, and the bot uses the new prices from the next order onward

### Requirement: Log viewing
The system SHALL provide admin pages showing the most recent lines of `logs/wallet_ledger.log` and `logs/atm_webhook.log`.

#### Scenario: View recent ledger
- **WHEN** the operator opens the logs page after a manual credit
- **THEN** the `ADMIN_CREDIT` line for that credit is visible in the ledger view
