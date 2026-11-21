# CHECKPOINT 4820 — read me first, then TASKS.md

**Written:** 2026-11-21T06:40:31Z · **tests:** all 11 fast checks green
**Branch:** `ccr-bc2a800c-fgp2ck` · **builds on:** `83979cb13` (this checkpoint is the commit after it)

## Just done
CS1b: Novig answered MAKER_BARRED to the maker at 06:37:06 UTC (confirm rate 10/10, no lock, 290 quotes in 17 min), 15 min after reserveByWinRate went live; reverted reserveByWinRate=false (f786b4101), clean runner requested (cebd9bee6); cause unknown, Tj must contact Novig

## Do this next
check whether the new runner is barred again (status rfq.halted/refusals); if still barred tell Tj to contact Novig with the maker id + time; do NOT re-enable reserveByWinRate; LP1 re-judge 06:47 and ON2 monitor ~06:52 continue

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M research/review/CHANGES.md

## Last ten checkpoints
```
  83979cb13 ckpt 4819: LP1b: rfq-pnl-study.yml fixed (numpy venv, calibration selftest inside the 
  a91955bfe ckpt 4818: CS1: win-rate-weighted quote collateral hold (RfqConfig.reserveByWinRate, R
  6e2182c81 ckpt 4817: ON2 monitor check-in #3 (06:07 UTC): healthy; maker connected, not halted, 
  016b64744 ckpt 4816: LP1: in-play parlays LIVE (liveParlays LIVE, per-leg cap .8, ~3 pt margin) 
  5aa2d1d57 ckpt 4815: Handoff saved (.handoff/handoff.md + archive 2026-11-21T06-00); CLAUDE.md/A
  0091deef3 ckpt 4814: FV2: rfq profile widened on graded evidence (minFair .35->.25: .25-.35 band
  50c1e2292 ckpt 4813: BL1: delayed syncNow on resume (blurry return), compile checked
  11ef34574 ckpt 4813: FV1 analysis: research/parlay_fair_coverage_2026-11-21.md (weighted attribu
  d38120752 ckpt 4812: ON2 monitor check-in #2 (05:10 UTC): healthy; maker connected, not halted, 
  e76d99932 ckpt 4811: ON2 monitor check-in #1 (04:22 UTC): healthy; maker connected, not halted, 
```
