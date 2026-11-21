# CHECKPOINT 4821 — read me first, then TASKS.md

**Written:** 2026-11-21T06:54:36Z · **tests:** all 11 fast checks green
**Branch:** `ccr-bc2a800c-fgp2ck` · **builds on:** `6a51cec1d` (this checkpoint is the commit after it)

## Just done
CS1b: Novig maintenance (423 SYSTEM_LOCKED 'Trading is paused: Maintenance') was the cause of the 06:37 MAKER_BARRED, not CS1; fixed slow recovery: RfqReconnect SYSTEM_LOCKED retry 90s (was 30 min), CloudTrader SYSTEM_LOCKED stop re-checked after 2 min (was 30), MAKER_BARRED held 30 min not until next job; tests + full floor 4547 green; CS1 still off (reserveByWinRate=false)

## Do this next
land on main (check), engine_set --reset so the running job has the faster retries, then watch for Novig reopening: maker connects, quotes; when it has quoted normally for a while re-enable rfq_set reserveByWinRate=true; LP1 re-judge (06:47) + ON2 monitor (06:52) are queued

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  9b9f9b7d0 ckpt 4820: CS1b: Novig answered MAKER_BARRED to the maker at 06:37:06 UTC (confirm rat
  83979cb13 ckpt 4819: LP1b: rfq-pnl-study.yml fixed (numpy venv, calibration selftest inside the 
  a91955bfe ckpt 4818: CS1: win-rate-weighted quote collateral hold (RfqConfig.reserveByWinRate, R
  6e2182c81 ckpt 4817: ON2 monitor check-in #3 (06:07 UTC): healthy; maker connected, not halted, 
  016b64744 ckpt 4816: LP1: in-play parlays LIVE (liveParlays LIVE, per-leg cap .8, ~3 pt margin) 
  5aa2d1d57 ckpt 4815: Handoff saved (.handoff/handoff.md + archive 2026-11-21T06-00); CLAUDE.md/A
  0091deef3 ckpt 4814: FV2: rfq profile widened on graded evidence (minFair .35->.25: .25-.35 band
  50c1e2292 ckpt 4813: BL1: delayed syncNow on resume (blurry return), compile checked
  11ef34574 ckpt 4813: FV1 analysis: research/parlay_fair_coverage_2026-11-21.md (weighted attribu
  d38120752 ckpt 4812: ON2 monitor check-in #2 (05:10 UTC): healthy; maker connected, not halted, 
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
