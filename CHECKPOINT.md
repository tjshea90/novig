# CHECKPOINT 4818 — read me first, then TASKS.md

**Written:** 2026-11-21T06:16:55Z · **tests:** all 11 fast checks green
**Branch:** `ccr-bc2a800c-fgp2ck` · **builds on:** `ebb1c2679` (this checkpoint is the commit after it)

## Just done
CS1: win-rate-weighted quote collateral hold (RfqConfig.reserveByWinRate, RfqMaker.cashHeld, poissonQuantile), test + full floor green 4546; cash-short refused 325 requests vs 36 quotes per 5 min after FV2 (correction to my earlier 'cash not limiting')

## Do this next
land on main, rfq_set reserveByWinRate=true then engine_set --reset, verify cash-short falls and quotes/min rises, no maker_insolvent halt; revert rfq_set reserveByWinRate=false

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M research/review/CHANGES.md

## Last ten checkpoints
```
  6e2182c81 ckpt 4817: ON2 monitor check-in #3 (06:07 UTC): healthy; maker connected, not halted, 
  016b64744 ckpt 4816: LP1: in-play parlays LIVE (liveParlays LIVE, per-leg cap .8, ~3 pt margin) 
  5aa2d1d57 ckpt 4815: Handoff saved (.handoff/handoff.md + archive 2026-11-21T06-00); CLAUDE.md/A
  0091deef3 ckpt 4814: FV2: rfq profile widened on graded evidence (minFair .35->.25: .25-.35 band
  50c1e2292 ckpt 4813: BL1: delayed syncNow on resume (blurry return), compile checked
  11ef34574 ckpt 4813: FV1 analysis: research/parlay_fair_coverage_2026-11-21.md (weighted attribu
  d38120752 ckpt 4812: ON2 monitor check-in #2 (05:10 UTC): healthy; maker connected, not halted, 
  e76d99932 ckpt 4811: ON2 monitor check-in #1 (04:22 UTC): healthy; maker connected, not halted, 
  6480fc59e ckpt 4810: ON2 DEPLOYED: loss-stop fix live (run 44863668546: maker connected, not hal
  4a1fd76e7 ckpt 4809: ON2: RfqConfig.lossStopFromMs + RfqLedger.Summary.pnlStop24h: the maker's l
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
