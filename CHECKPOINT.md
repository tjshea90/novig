# CHECKPOINT 552 — read me first, then TASKS.md

**Written:** 2026-09-28T04:15:45Z · **tests:** all 1 fast checks green
**Branch:** `claude/scan-perf-background-notifications-ig8u0c` · **builds on:** `2d52256` (this checkpoint is the commit after it)

## Just done
Released Vigilant v0.18.0 (code 34): release.yml run 36376581625 green, tag v0.18.0 carries vigilant-v0.18.0.apk only, BUILDLOG recorded, P1-P6 ticked; release.yml notes mention opt-in auto-scan

## Do this next
Nothing in flight. Tj to try auto-scan on the phone (Settings > Background auto-scan; allow notifications + Unrestricted battery); on-device behavior (alarms in Doze, alert tap) not verifiable here

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M .github/workflows/release.yml
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  1db8616 ckpt 551: pre-release: v0.18.0: background auto-scan (CNO or CNO + Vigilant, every 5-40 
  ee1cfa9 ckpt 550: P6: live Novig/finder/CNO/scores green; 110 screenshots green (5d, 5e looked a
  6259bbe ckpt 549: P6 full tests: fix F1 duplicate alerts when two scans end together (send mutex
  45fbbf7 ckpt 548: P1-P5 done and ticked: floor 654 tests, 0 failures, 8 live skipped (engine/dat
  3b700a0 ckpt 547: P4/P5 tests: AutoScanTest 13, AgreementTest 3, AlertLogTest 3, NovigLive.readN
  25a8521 ckpt 546: P4/P5 code written, compiles: AutoScanService (specialUse FGS, exact alarms, b
  8cb689e ckpt 545: P1+P2 done, P3 mostly: prices/scan to 1200, props/game 16/24, odds cap +120/+1
  6c91990 ckpt 544: Recorded Tj's 03:19Z request as P1-P6 in TASKS.md
  5f220e0 ckpt 543: Released Vigilant v0.17.1 (code 33): release.yml run 36365476060 green, tag v0
  a0a82b7 ckpt 542: Removed an unrelated side job from this repo (Tj: this repo is Vigilant only):
```
