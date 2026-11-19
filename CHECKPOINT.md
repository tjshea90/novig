# CHECKPOINT 4700 — read me first, then TASKS.md

**Written:** 2026-11-19T18:15:26Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `fcbd78719` (this checkpoint is the commit after it)

## Just done
MK13b: docs audit written (research/novig_rfq_docs_audit_2026-11-19.md), NOVIG_API.md 33.3, CHANGES/TASKS, LEVERS; cash-aware LIVE sizing + optional dedicated maker key wired in CloudMain; FULL suite green (4446 tests)

## Do this next
land, engine reset, then: concentration cap per leg (6 of 13 live fills rode the Houston game), read executions in SHADOW for the 13 live parlays' results

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  422e9a32a ckpt 4699: MK13a: Novig docs audit fixes in the RFQ maker: reconnect rules (RfqReconne
  5ca44ed7e ckpt 4698: MK11 17:38: ladder lifts priced trades 0.5% -> 19% on paper
  5517a1085 ckpt 4697: MK12 round 2 complete and deployed in SHADOW: last-look fix, SameGame copul
  36e09e625 ckpt 4696: MK11: ladder live in shadow; engine outage 16:59-17:10 repaired (dispatch),
  076fb99a1 ckpt 4695: MK12h: rfq-market.yml retries refused calls (the workflow token's 1,000 an 
  44115cb5c ckpt 4694: MK12g: RfqMarketModel.MIN_SUPPORT (5 answers above a candidate margin), wal
  7088a7870 ckpt 4693: MK12f: model floor fairErrorRel (fair x 2% x sqrt(legs)), xs band, via-quot
  12977b6eb ckpt 4692: MK12e: SHADOW capital fix, propPairsFromMarket (OFF), Parlays-view probe/mo
  6d511ca17 ckpt 4691: MK11: RfqLadder prop rung interpolation wired into the rfq leg fairs
  c4ae5ea25 ckpt 4691: MK12d: SHADOW judges RFQs as if the wallet were free (capital cap only in L
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
