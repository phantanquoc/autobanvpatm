## ADDED Requirements

### Requirement: VND item pricing
The system SHALL interpret the price column of `BanVatPham/items/*.txt` (`id:price:maxQty:maVp`) as direct VND per item. Prices SHALL NOT be converted from or to in-game xu. Help and broadcast messages SHALL quote VND prices.

#### Scenario: Price quoted in VND
- **WHEN** a customer views prices or receives a purchase quote
- **THEN** all amounts are VND amounts taken directly from the item file

### Requirement: Purchase paid from wallet
The system SHALL keep the existing purchase chat syntax (`<qty> <maVp>`, either order) and stock checks. When stock is fulfillable (including via the existing storage-withdraw behavior), the bot SHALL check the buyer's wallet available balance against `price × qty`: if insufficient, no hold is placed and the bot replies with the shortage and deposit instructions; if sufficient, the bot SHALL place a hold for the total, then run the existing flow — prepare stock, trade invite (opcode 43), offer the items at 0 xu — and confirm the trade when the buyer locks it (case 45), regardless of xu offered.

#### Scenario: Successful wallet purchase
- **WHEN** a customer with sufficient available balance buys `12 d7` and locks the trade
- **THEN** the bot confirms, and on trade completion the hold is captured (balance decreases by the total) and the existing sale logging runs

#### Scenario: Insufficient balance
- **WHEN** the total exceeds the customer's available balance
- **THEN** no hold and no trade occur, and the bot replies with the shortage amount and how to top up

#### Scenario: Purchases no longer accept xu
- **WHEN** a buyer offers xu in a trade not initiated through a wallet purchase
- **THEN** the existing ban-only guard cancels the trade; xu is never accepted as payment

### Requirement: Hold release on failure
The system SHALL release the purchase hold, with no balance change, when the trade is cancelled (case 57), times out (90 seconds), the buyer cannot be found in the map, stock preparation fails, or the trade window never opens.

#### Scenario: Buyer cancels trade
- **WHEN** a wallet purchase trade is cancelled
- **THEN** the hold is released and the customer can immediately spend the same funds again

#### Scenario: Trade timeout
- **WHEN** a wallet purchase does not complete within 90 seconds
- **THEN** the trade is cancelled, the hold is released, and the customer is notified

### Requirement: NIK selling removed
The system SHALL NOT contain any NIK selling behavior: no NIK classes (`NikSeller`, `NikStore`, `NikEntry`, `NikFrame`), no "Quan ly NICK" GUI button, no `nick`/`[NICK]` routing in `BanSeller`, and no NIK specs under `openspec/specs/`. Messages mentioning nick SHALL be handled as ordinary item-shop messages (help), never routed to a NIK flow.

#### Scenario: Nick message after removal
- **WHEN** a customer chats a message containing "nick"
- **THEN** the bot responds with the normal item-shop help and no NIK flow, error, or dead code path is triggered
