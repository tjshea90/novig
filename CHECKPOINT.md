# CHECKPOINT 4274 — read me first, then TASKS.md

**Written:** 2026-10-26T02:01:43Z · **tests:** all 9 fast checks green
**Branch:** `ccr-db238840-epvfzh` · **builds on:** `d7e094282` (this checkpoint is the commit after it)

## Just done
PX11e: propline.yml task 'shape' prints a league's raw board shape to the log (to build PropLine tennis from a real answer)

## Do this next
dispatch propline.yml task=shape sports=tennis on main; build PropLine tennis (SGO-only Pinnacle lines are mostly WTA/ATP)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  5c61a51cb ckpt 4273: PX14b done: results open 23%->2.1% (engine settled 182/208 from Novig's led
  63c670539 ckpt 4272: PX14b: open orders settled by the engine from Novig's ledger (TradeSettle.f
  73e2cc751 ckpt 4271: PX14b measured: CLV 59%->83%, results open 23%->15%; Novig fallback settled
  256a93a15 ckpt 4270: dispatched engine-grade.yml + propline.yml clv on main (PX14b code landed)
  ae6a27287 ckpt 4269: PX14b: Novig's own close wired into the CLV job (propline_clv_job.sh: order
  98e213980 ckpt 4268: PX14: results gap (23% open) fixed in code via Novig's own settlement (Grad
  fe084774f ckpt 4267: PX13b: Kalshi quotes kept current between scans while the socket vouches an
  33cd0c5d5 ckpt 4266: PX13a answered (ParlayAPI carries 4 books on that game); PX13c census pinnS
  a6f817fa0 ckpt 4265: PX12 landed on main, engine reset; PX12c answered (no socket contention)
  29d2da2d3 ckpt 4264: PX12: full scan every 2 min, not 6 (far games' fair was 295 s old on every 
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
