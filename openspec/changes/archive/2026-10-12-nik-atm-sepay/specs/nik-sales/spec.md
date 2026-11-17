## MODIFIED Requirements

### Requirement: Chat routing between NIK and item sales
The system SHALL route incoming private messages containing the token `nik` (case-insensitive, after trimming and lowercasing) to the NIK purchase flow; additionally when `ATM=1` messages matching `mua <qty> <loai>` where `loai` is in `nik_loai.txt` SHALL also route to the NIK ATM flow even without the `nik` token (e.g. `mua 2 ttc`, `mua ctt 2`). All other messages SHALL follow the existing item-sale flow. The routing SHALL occur before item parsing and SHALL not alter BanSeller item behavior when `ATM=0`.

#### Scenario: nik keyword routes to NIK flow
- **WHEN** buyer sends `mua nik 2`
- **THEN** the NIK flow is invoked and the item flow is not

#### Scenario: mua loai without nik token routes to ATM when ATM=1
- **WHEN** `ATM=1`, `nik_loai.txt` contains `ttc`, buyer sends `mua 2 ttc` (no `nik` token)
- **THEN** the NIK ATM flow is invoked

#### Scenario: Non-nik message stays on item flow when ATM=0
- **WHEN** `ATM=0` and buyer sends `mua 2 ttc` without `nik` token
- **THEN** the item flow is invoked (existing behavior)

#### Scenario: Non-nik message stays on item flow
- **WHEN** buyer sends `mua d7 5`
- **THEN** the item flow is invoked and the NIK flow is not
