# CHECKPOINT 4702 — read me first, then TASKS.md

**Written:** 2026-11-19T18:31:05Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `4e472a9d7` (this checkpoint is the commit after it)

## Just done
MK13c: per-outcome collateral cap (maxLegCollateral $30, LIVE), executions read in SHADOW (results of the 13 live parlays), rfq_set cashReserve/maxLegCollateral, audit rows 9-10; FULL suite green (4448 tests)

## Do this next
engine_set --reset onto main (only one reset: board says MK12/MK13 and MK11 share the engine), confirm startedAtMs, status.rfq.sockets/standing/settled; then analyse the probe journals

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6b65df2ba ckpt 4701: WATCH 2026-11-19: engine healthy, no action
  66b41859f ckpt 4700: MK13b: docs audit written (research/novig_rfq_docs_audit_2026-11-19.md), NO
  422e9a32a ckpt 4699: MK13a: Novig docs audit fixes in the RFQ maker: reconnect rules (RfqReconne
  5ca44ed7e ckpt 4698: MK11 17:38: ladder lifts priced trades 0.5% -> 19% on paper
  5517a1085 ckpt 4697: MK12 round 2 complete and deployed in SHADOW: last-look fix, SameGame copul
  36e09e625 ckpt 4696: MK11: ladder live in shadow; engine outage 16:59-17:10 repaired (dispatch),
  076fb99a1 ckpt 4695: MK12h: rfq-market.yml retries refused calls (the workflow token's 1,000 an 
  44115cb5c ckpt 4694: MK12g: RfqMarketModel.MIN_SUPPORT (5 answers above a candidate margin), wal
  7088a7870 ckpt 4693: MK12f: model floor fairErrorRel (fair x 2% x sqrt(legs)), xs band, via-quot
  12977b6eb ckpt 4692: MK12e: SHADOW capital fix, propPairsFromMarket (OFF), Parlays-view probe/mo
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
