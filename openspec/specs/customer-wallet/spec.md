## ADDED Requirements

### Requirement: Wallet creation and identity
The system SHALL auto-create a wallet the first time a game character interacts with any shop bot (wallet view, deposit info, purchase, or transfer receipt). The creating character SHALL be the wallet's primary account. Each wallet SHALL have a unique permanent deposit code of the form `VI` followed by 6 digits.

#### Scenario: First interaction creates wallet
- **WHEN** a character with no wallet chats a wallet command to any bot
- **THEN** a wallet is created with that character as primary, balance 0, and a unique `VI######` deposit code, persisted immediately

#### Scenario: Same wallet at every bot
- **WHEN** the same character interacts with a different bot of the shop
- **THEN** the bot sees the same wallet, balance, and deposit code (no second wallet is created)

### Requirement: Wallet persistence and ledger
The system SHALL persist wallets to a file in the bot working directory using temp-file-then-rename writes, and SHALL append one ledger line to an append-only log under `logs/` for every credit, hold, capture, release, transfer, link, and unlink. All wallet mutations SHALL be synchronized on a single store shared by all bots in the JVM, so concurrent operations cannot overspend a wallet.

#### Scenario: Restart preserves balances
- **WHEN** the bot process restarts
- **THEN** wallet balances, linked characters, and deposit codes are exactly as before the restart

#### Scenario: Concurrent purchases cannot overspend
- **WHEN** two bots process purchases against the same wallet at the same time and the combined total exceeds the balance
- **THEN** only purchases covered by the available balance proceed; the rest are refused as insufficient

### Requirement: Available balance and holds
The system SHALL compute available balance as balance minus the sum of active holds. Placing a hold SHALL NOT change the balance; capturing a hold SHALL deduct its amount from the balance; releasing a hold SHALL free its amount with no balance change. Holds SHALL exist only in memory and SHALL NOT survive a restart.

#### Scenario: Hold reduces availability
- **WHEN** a wallet with 100000 VND balance has a 30000 VND hold
- **THEN** its available balance is 70000 VND and its balance remains 100000 VND until capture

### Requirement: Wallet chat commands
The system SHALL provide simple in-game private-chat commands for: viewing wallet (balance, available balance, deposit code, linked characters), deposit info (bank account, deposit code, VietQR), linking, unlinking, and transferring. There SHALL be no wallet GUI.

#### Scenario: View wallet
- **WHEN** a customer chats the wallet-view command
- **THEN** the bot replies privately with balance, available balance, deposit code, and linked character names

### Requirement: Account linking
The system SHALL allow a wallet's primary character to invite another character by chat command. The invited character SHALL confirm by chat command within 15 minutes; after confirmation both characters belong to the same wallet and either may spend or transfer. Only the primary character SHALL be able to unlink a character. Expired or unconfirmed invites SHALL have no effect.

#### Scenario: Link secondary account
- **WHEN** the primary chats the link command naming a secondary character and that character chats the confirm command within 15 minutes
- **THEN** the secondary character is linked and purchases from it debit the shared wallet

#### Scenario: Expired invite
- **WHEN** the invited character confirms more than 15 minutes after the invite
- **THEN** the link is refused and the invite is discarded

### Requirement: Wallet transfer
The system SHALL allow any linked character to transfer a positive VND amount to another game account via chat command with zero fee. If the recipient has no wallet, one SHALL be auto-created with the recipient as primary. The sender's available balance MUST cover the amount. Both parties SHALL receive a private confirmation when online.

#### Scenario: Transfer to account without wallet
- **WHEN** a customer transfers 50000 VND to a character that has no wallet
- **THEN** a wallet is created for that character, credited 50000 VND, and the sender's balance decreases by 50000 VND

#### Scenario: Transfer exceeding available balance
- **WHEN** a customer transfers more than their available balance
- **THEN** the transfer is refused, no balances change, and the bot replies with the available balance

### Requirement: No withdrawal or automatic refund
The system SHALL NOT provide withdrawal of wallet funds to a bank account and SHALL NOT automatically refund wallet funds. Failed or cancelled purchases SHALL be handled solely by releasing holds (no balance movement).

#### Scenario: Cancelled purchase moves no money
- **WHEN** a purchase with a hold is cancelled
- **THEN** the hold is released and the wallet balance is unchanged
