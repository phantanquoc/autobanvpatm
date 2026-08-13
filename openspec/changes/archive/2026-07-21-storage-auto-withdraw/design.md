## Context

The selling bot is obfuscated Java (class names `Class_cd`, `Class_ai`, etc.) with no original source; edits are made to a small set of patcher `.java` files in `BanVatPham/` and rebuilt into `ninja_ban.jar`. Over 6 live tests, the storage chest mechanics were fully verified (see memory `muado-tool-architecture.md`):

- Chest = `cd.q.x` (`Class_al[30]`), per-slot quantity in `.e`, slot index == `.a`. Every chest slot holds `e=1` (non-stacking stones).
- Reading chest only needs `i(4)` = `Class_bo((byte)-30)`+`writeByte(-103)`+`writeByte(4)` sent from the selling spot; server pushes `cd.q.x` ~500ms later via `Class_cq`. `cd.q.x` is null until the first `i(4)`.
- Withdraw requires being AT the storage NPC (type 5, same zone as selling): `cd.h.d(5)` → `cd.h.a(aa,ab)` (repeat to resist pull-back) → greet opcode `29 (5,0,0)` → `i(4)` → opcode `16 writeByte(slot)` per slot.
- Server updates bag `.v` with >4s delay after a withdraw.

The packet receiver `Class_cq` calls `onPrivateMessage`/`onTradeOffer` on the receive thread — blocking there deafens the bot (known past bug). Existing patterns to reuse: `ensureBroadcast()` hook in `Class_cp.a()` for idle-tick work, and `BanSeller$1`/`StorageTester$1` for off-thread work with sleeps.

## Goals / Non-Goals

**Goals:**
- Display real chest stock per item on the GUI "RUONG" column.
- Automatically refill the bag from the chest when a listed item's bag count hits 0, until bag full or chest empty of that item.
- Trigger both proactively (idle background) and reactively (buyer top-up).
- Never block the receive thread; never abandon an active trade.

**Non-Goals:**
- No cross-map/zone travel (storage NPC is in the selling zone).
- No deposit (opcode 17) — withdraw only.
- No change to the selling flow (quote/invite/split/lock/confirm/log).
- No live testing by the bot pipeline — the operator runs live tests.

## Decisions

**D1: Reading vs withdrawing are separated.** Counting/GUI uses only `i(4)` from the selling spot (no movement). Only withdraw walks to the NPC. Rationale: verified in test #2 that `i(4)` returns `cd.q.x` from the selling spot without moving — cheap and non-disruptive for the GUI, so we don't drag the bot to the NPC just to display stock.

**D2: Refresh on a throttled timer, hooked in the idle tick.** Send `i(4)` at most once per 30s, only when IDLE and not withdrawing, hooked where `ensureBroadcast()` already runs (`Class_cp.a()`). Rationale: keeps `cd.q.x` fresh for GUI + withdraw decisions without spamming the server. Alternative (refresh every tick) rejected — spams `-30` and risks server throttling.

**D3: Withdraw runs on a dedicated thread guarded by an in-progress flag.** A `withdrawing` boolean gates entry; the thread does move+poll+greet+open+withdraw with paced sleeps (~50–100ms/slot), mirroring the original farm bot's deposit loop (`Class_ce` with `Class_bb.b(50L)`). Rationale: movement and polling need sleeps, which are forbidden on the receive thread. Alternative (do it inline in the tick) rejected — the tick also runs in a context where long blocking would stall other periodic work.

**D4: Withdraw only starts from IDLE (background) or as a pre-trade top-up.** Never during WAITING/IN_TRADE. Rationale: leaving to walk to the NPC mid-trade would break the sale. The top-up path triggers before the bot commits to a trade.

**D5: Do not block waiting for bag sync after withdraw.** After sending withdraw opcodes, let subsequent ticks re-read `cd.q.v`; optionally call `cd.q.b()` to recompute bag counts. Rationale: server bag update is delayed >4s; blocking would stall the bot. Alternative (poll until bag reflects withdraw) rejected — unreliable timing, wastes the thread.

**D6: Stop condition = bag full OR chest empty of item.** Count empty `.v` slots; withdraw matching `.x` slots one by one until no empty bag slot remains or no matching chest slot remains. Rationale: matches the user's chosen "fill until bag full" behavior for non-stacking items.

## Risks / Trade-offs

- [Server rejects withdraw if greet/open timing is too fast] → Pace the sequence (greet → ~800ms → open → poll `.x` → withdraw with 50–100ms/slot), as in the verified test.
- [Bot pulled back to config position mid-walk by `Class_ce`] → Re-issue `cd.h.a(aa,ab)` each ~500ms poll until within 20 units (verified effective in test #6).
- [Bag `.v` shows stale count right after withdraw, causing a re-trigger] → Guard with `withdrawing` flag during the operation and rely on delayed re-read; do not loop-trigger on stale data.
- [Clean rebuild drops `icon.png`, bot won't start] → Verify `jar tf | grep icon.png` after every build; re-add with `jar uf` if missing.
- [Withdraw thread interferes with broadcast/selling] → Only run when IDLE; set `withdrawing` so quote/invite paths can defer or the tick skips refresh.

## Migration Plan

- Additive change; no data migration. Deploy = rebuild jar, operator restarts bot.
- Rollback = restore a pre-change jar backup (`ninja_ban.jar.bak-*`).
- StorageTester `testkho` debug tool remains for re-verification.

## Open Questions

- None blocking. Exact refresh interval (30–60s) and per-slot pace (50–100ms) are tunable during live testing without design change.
