## ADDED Requirements

### Requirement: SePay webhook endpoint
The system SHALL run an HTTP webhook endpoint `POST /webhook/sepay` on the configured `ATM_PORT` (default 8089) only when the `ATM` config tag is `1`. It SHALL verify the configured `SEPAY_SECRET` (Authorization or secret header, allowing `Bearer`/`Apikey` prefixes) and reject requests with an invalid secret. It SHALL parse amount and transfer content and transaction id from the SePay JSON field variants (`transferAmount`/`amount`, `transferContent`/`content`/`description`/`addInfo`, `transaction_id`/`referenceCode`/`id`/`transactionId`).

#### Scenario: Webhook disabled when ATM off
- **WHEN** the `ATM` config tag is not `1`
- **THEN** no webhook server is started and bot behavior is unchanged otherwise

#### Scenario: Invalid secret rejected
- **WHEN** a webhook request arrives without the correct secret
- **THEN** the request is rejected with HTTP 401 and no wallet is credited

### Requirement: Credit wallet by permanent deposit code
The system SHALL credit a wallet when the transfer content contains that wallet's permanent deposit code (case-insensitive). Any positive amount SHALL be credited in full (deposits are not tied to orders and have no expected amount or expiry). The credited customer SHALL be notified by private chat when online, showing the credited amount and new balance.

#### Scenario: Successful top-up
- **WHEN** a valid webhook reports 200000 VND with content containing a wallet's deposit code
- **THEN** that wallet's balance increases by 200000 VND, a ledger line is written, and the customer is notified if online

#### Scenario: Unmatched content
- **WHEN** a valid webhook's content contains no known deposit code
- **THEN** no wallet is credited and the event is logged only

### Requirement: Anti-replay
The system SHALL persist processed transaction ids (`atm_seen.log`) and SHALL reject any webhook whose transaction id was already processed, without crediting again. Seen ids SHALL survive restarts.

#### Scenario: Replayed transaction
- **WHEN** the same transaction id is delivered twice
- **THEN** the wallet is credited exactly once and the replay is answered as duplicate

### Requirement: ATM configuration
The system SHALL read `ATM`, `BANK_CODE`, `BANK_STK`, `BANK_NAME`, `SEPAY_SECRET`, and `ATM_PORT` tags from `banvatpham.txt`, defaulting to ATM off and port 8089. Deposit instructions sent to customers SHALL include the bank account, the wallet's deposit code as the required transfer content, and a VietQR URL when a bank code is configured.

#### Scenario: Deposit instructions
- **WHEN** a customer asks how to top up
- **THEN** the bot replies with the configured bank account/name, their own deposit code, and the VietQR URL
