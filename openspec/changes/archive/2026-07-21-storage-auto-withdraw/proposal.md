## Why

The selling bot only sells items sitting in its inventory bag (`cd.q.v`). When a listed item runs out in the bag, the bot reports "out of stock" even though a large reserve of that item sits in the account's storage chest (`cd.q.x`). The operator must manually withdraw items from the chest to keep selling. This change lets the bot see its chest stock on the GUI and automatically refill the bag from the chest when an item runs low, so it can keep selling unattended.

## What Changes

- Add periodic chest refresh: the bot sends the open-chest request `i(4)` from the selling spot on a low-frequency timer so `cd.q.x` (chest data) stays populated for both the GUI and the withdraw logic.
- Show a real chest-stock column on the GUI ("RUONG") listing quantity per price-list item (e.g. `d7=8, d8=13, d9=9`), read from `cd.q.x` by mã vp.
- Add automatic withdraw: when a price-list item's bag count reaches 0 (or is depleted) and the chest still holds that item, the bot walks to the storage NPC (type 5, same zone), opens a chest session, and withdraws that item slot-by-slot until the bag is full or the chest is empty of that item.
- Run withdraw in two triggers: (a) background when idle (no active trade) — proactively refill items that are empty; (b) top-up when a buyer orders an item whose bag stock just ran out.
- All withdraw work (movement, waiting, polling) runs on a dedicated thread, never on the packet-receive thread; a withdraw-in-progress flag prevents overlapping withdraws and prevents leaving an active trade.

## Capabilities

### New Capabilities
- `storage-chest`: Reading/counting the account storage chest, displaying chest stock on the GUI, and automatically withdrawing items from the chest into the selling bag when bag stock is depleted.

### Modified Capabilities
<!-- None: the existing selling flow (quote/invite/split/lock/confirm) is unchanged; withdraw only intervenes while idle or before a trade begins. -->

## Impact

- **Code (BanVatPham/ only, gốc MuaDo\ untouched):**
  - `BanSeller.java` — chest counting, refresh trigger, auto-withdraw logic + state, dedicated withdraw thread (inner class).
  - `Class_cw_edit.java` — "RUONG" GUI column (wiring already present, must read correct field).
  - `Class_cp_edit.java` — hook the background refresh/withdraw tick (same pattern as `ensureBroadcast`).
  - `StorageTester.java` — kept as the `testkho` debug tool, not removed.
- **Verified protocol facts (from 6 live tests):** chest = `cd.q.x[30]`, quantity in `.e`; open = opcode `-30/-103/writeByte(4)`; withdraw sequence = find NPC `cd.h.d(5)` → move `cd.h.a(aa,ab)` → greet opcode `29 (5,0,0)` → open `i(4)` → withdraw opcode `16 writeByte(slot)` per slot.
- **Build:** clean rebuild; `icon.png` must remain in jar; `main.Midlet` manifest; StorageTester 4 classes kept; `r=false` patch preserved.
- **Constraint:** server updates bag `.v` with >4s delay after withdraw — logic must not block waiting for sync.
