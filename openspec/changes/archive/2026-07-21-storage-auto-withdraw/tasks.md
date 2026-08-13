## 1. Chest counting + GUI (verify existing agent work)

- [x] 1.1 In `BanSeller.java`, verify `countInStorage(id)` reads `cd.q.x` with quantity field `.e` (not `.w`/`.d`); fix if wrong. Match item id via `al.b.a & 0xFFFF`, null-safe.
- [x] 1.2 Verify `getStorageSummary()` iterates the price list, skips items without mã vp, formats `<maVp>=<countInStorage(id)>` joined by ", ", returns "-" when price list empty or nothing to show.
- [x] 1.3 In `Class_cw_edit.java`, verify the "RUONG" column (header + `[9]` array width + `objectArray2[n][8]` = `getStorageSummary()`) is wired and null-safe. ← (verify: GUI column reads chest via cd.q.x/.e, shows "-" before first i(4))

## 2. Chest refresh timer

- [x] 2.1 Add a throttled chest-refresh helper in `BanSeller.java` that sends `i(4)` (`new Class_bo((byte)-30)`+`writeByte(-103)`+`writeByte(4)` via `cd.l.a.i.a(bo)`, `bo.b()` in finally); no more than once per 30s (track last-refresh time).
- [x] 2.2 Guard the refresh: only send when state is IDLE and no withdraw in progress.
- [x] 2.3 Hook the refresh into the idle background tick (same site as `ensureBroadcast()` in `Class_cp_edit.java` / `Class_cp.a()`). ← (verify: i(4) sent at most 1/30s, only when idle, cd.q.x populated for GUI without moving the bot)

## 3. Withdraw sequence (dedicated thread)

- [x] 3.1 Add a `withdrawing` in-progress flag + a method to launch withdraw on a dedicated thread (inner class like `BanSeller$1`); ignore new triggers while `withdrawing` is true.
- [x] 3.2 Implement NPC locate + move: `Class_at npc = cd.h.d(5)`; if null, abort cleanly. Else loop re-issuing `cd.h.a(npc.aa, npc.ab)` every ~500ms until `|cd.q.aa-npc.aa|+|cd.q.ab-npc.ab| <= 20` (cap total wait, e.g. 10s).
- [x] 3.3 Send greet opcode `29`: `new Class_bo((byte)29)`+`writeByte(5)`+`writeByte(0)`+`writeByte(0)`, then wait ~800ms.
- [x] 3.4 Send open `i(4)` and poll `cd.q.x` until populated (cap ~8s); abort cleanly if still null.
- [x] 3.5 Withdraw loop: for the target item id, withdraw matching `cd.q.x` slots one at a time via `new Class_bo((byte)16)`+`writeByte(slot)`, ~50–100ms pace between slots; stop when bag has no empty `.v` slot OR no matching chest slot remains. ← (verify: sequence matches verified order 29→i(4)→16, per-slot pacing, stops on bag-full or chest-empty, all on a non-receive thread)

## 4. Withdraw triggers + safety

- [x] 4.1 Background trigger: in the idle tick, when a price-list item's `countAvailable` bag count is 0 and `countInStorage` > 0, launch withdraw for that item (only when state IDLE).
- [x] 4.2 Top-up trigger: when a buyer orders an item whose bag count is 0 but chest holds it, launch withdraw before rejecting for stock — dispatched to the withdraw thread, `onPrivateMessage` returns immediately (no sleep/block on receive thread).
- [x] 4.3 Ensure no withdraw starts while state is WAITING/IN_TRADE; after withdraw completes, resume toward the selling position (let `Class_ce` pull back or move to `cd.f.e`/`cd.f.f`). ← (verify: no blocking on receive thread, no overlapping withdraws, active trade never abandoned, selling flow unchanged)

## 5. Build + verify jar

- [x] 5.1 Compile `javac -encoding UTF-8 --release 8 -cp ninja_ban.jar` for BanSeller.java + StorageTester.java + Class_cw_edit.java (+ Class_cp edit file if used). Backup jar first.
- [x] 5.2 Clean rebuild (unzip -o → cp changed classes → `jar cfe main.Midlet`), never `jar uf` for classes.
- [x] 5.3 Verify jar: `jar tf | sort | uniq -d` empty; `icon.png` present (=1); Main-Class=main.Midlet; StorageTester 4 classes present; testkho hook present. ← (verify: icon.png retained, no duplicate classes, manifest intact, StorageTester + testkho preserved)
- [x] 5.4 Verify bytecode has withdraw sequence (opcode 29 + 16 + i(4)) and `cd.h.d`/`cd.h.a` calls; `getStorageSummary`/`countInStorage` read `.x`/`.e`; `r=false` patch at offset 578 preserved. ← (verify: withdraw opcodes + NPC API present in jar, r=false patch intact)
- [x] 5.5 Provide operator live-test instructions (restart bot, ensure bag has empty slots + chest has items, observe background/top-up withdraw + GUI RUONG column); do NOT claim live-tested.
