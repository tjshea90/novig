# CHECKPOINT 4819 — read me first, then TASKS.md

**Written:** 2026-11-21T06:38:06Z · **tests:** all 11 fast checks green
**Branch:** `ccr-bc2a800c-fgp2ck` · **builds on:** `6fbd72aee` (this checkpoint is the commit after it)

## Just done
LP1b: rfq-pnl-study.yml fixed (numpy venv, calibration selftest inside the publish-anyway guard): my 06:02 dispatch failed on 'No module named numpy' and Publish was skipped, so the Release was stale since 11-20 18:48

## Do this next
land on main, re-dispatch rfq-pnl-study (days=4), then re-judge in-play margin on the new graded.csv.gz (LP1 follow-up); CS1 verify at 06:37 UTC also pending; ON2 monitor check-in #4 ~06:52

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M research/review/CHANGES.md

## Last ten checkpoints
```
  a91955bfe ckpt 4818: CS1: win-rate-weighted quote collateral hold (RfqConfig.reserveByWinRate, R
  6e2182c81 ckpt 4817: ON2 monitor check-in #3 (06:07 UTC): healthy; maker connected, not halted, 
  016b64744 ckpt 4816: LP1: in-play parlays LIVE (liveParlays LIVE, per-leg cap .8, ~3 pt margin) 
  5aa2d1d57 ckpt 4815: Handoff saved (.handoff/handoff.md + archive 2026-11-21T06-00); CLAUDE.md/A
  0091deef3 ckpt 4814: FV2: rfq profile widened on graded evidence (minFair .35->.25: .25-.35 band
  50c1e2292 ckpt 4813: BL1: delayed syncNow on resume (blurry return), compile checked
  11ef34574 ckpt 4813: FV1 analysis: research/parlay_fair_coverage_2026-11-21.md (weighted attribu
  d38120752 ckpt 4812: ON2 monitor check-in #2 (05:10 UTC): healthy; maker connected, not halted, 
  e76d99932 ckpt 4811: ON2 monitor check-in #1 (04:22 UTC): healthy; maker connected, not halted, 
  6480fc59e ckpt 4810: ON2 DEPLOYED: loss-stop fix live (run 44863668546: maker connected, not hal
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
