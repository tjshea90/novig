# CHECKPOINT 4277 — read me first, then TASKS.md

**Written:** 2026-10-26T02:17:06Z · **tests:** all 9 fast checks green
**Branch:** `ccr-db238840-epvfzh` · **builds on:** `cecb34fe4` (this checkpoint is the commit after it)

## Just done
PX13c/PX11e: PropLine tennis built (PropLineTennis, from a real answer), SGO kept for tennis-only markets; pinnAgeNF census; full test.sh green

## Do this next
engine_set.py --reset; read pinnFeeds/pinnSgoOnly/pinnAgeNF after a scan; then PX12b/PX10g/PX10d/PX9i and older items

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  206288359 ckpt 4276: pinnAgeNF census (PX10g near/far split) + shape tours
  b6167a5b6 ckpt 4275: PropLine tennis shape read (375 events, Pinnacle on 114, h2h/spreads/totals
  3d791e0e0 ckpt 4274: PX11e: propline.yml task 'shape' prints a league's raw board shape to the l
  5c61a51cb ckpt 4273: PX14b done: results open 23%->2.1% (engine settled 182/208 from Novig's led
  63c670539 ckpt 4272: PX14b: open orders settled by the engine from Novig's ledger (TradeSettle.f
  73e2cc751 ckpt 4271: PX14b measured: CLV 59%->83%, results open 23%->15%; Novig fallback settled
  256a93a15 ckpt 4270: dispatched engine-grade.yml + propline.yml clv on main (PX14b code landed)
  ae6a27287 ckpt 4269: PX14b: Novig's own close wired into the CLV job (propline_clv_job.sh: order
  98e213980 ckpt 4268: PX14: results gap (23% open) fixed in code via Novig's own settlement (Grad
  fe084774f ckpt 4267: PX13b: Kalshi quotes kept current between scans while the socket vouches an
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
