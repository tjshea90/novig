# CHECKPOINT 551 — read me first, then TASKS.md

**Written:** 2026-09-28T04:06:10Z · **tests:** all 1 fast checks green
**Branch:** `claude/scan-perf-background-notifications-ig8u0c` · **builds on:** `f8329ad` (this checkpoint is the commit after it)

## Just done
pre-release: v0.18.0: background auto-scan (CNO or CNO + Vigilant, every 5-40 min, with Vigilant closed) and +EV push alerts at 2/3/4%+ when 3+ books agree (tap opens the bet slip in Novig); up to 1,200 Novig prices a scan; scans price ~50x less CPU per update and re-read their first edges at the end; odds cap +120/+150/+200/+300. 656 tests (versionCode 34, v0.18.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.18.0), then run: bash tools/record-release.sh v0.18.0 34 "v0.18.0: background auto-scan (CNO or CNO + Vigilant, every 5-40 min, with Vigilant closed) and +EV push alerts at 2/3/4%+ when 3+ books agree (tap opens the bet slip in Novig); up to 1,200 Novig prices a scan; scans price ~50x less CPU per update and re-read their first edges at the end; odds cap +120/+150/+200/+300. 656 tests"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  ee1cfa9 ckpt 550: P6: live Novig/finder/CNO/scores green; 110 screenshots green (5d, 5e looked a
  6259bbe ckpt 549: P6 full tests: fix F1 duplicate alerts when two scans end together (send mutex
  45fbbf7 ckpt 548: P1-P5 done and ticked: floor 654 tests, 0 failures, 8 live skipped (engine/dat
  3b700a0 ckpt 547: P4/P5 tests: AutoScanTest 13, AgreementTest 3, AlertLogTest 3, NovigLive.readN
  25a8521 ckpt 546: P4/P5 code written, compiles: AutoScanService (specialUse FGS, exact alarms, b
  8cb689e ckpt 545: P1+P2 done, P3 mostly: prices/scan to 1200, props/game 16/24, odds cap +120/+1
  6c91990 ckpt 544: Recorded Tj's 03:19Z request as P1-P6 in TASKS.md
  5f220e0 ckpt 543: Released Vigilant v0.17.1 (code 33): release.yml run 36365476060 green, tag v0
  a0a82b7 ckpt 542: Removed an unrelated side job from this repo (Tj: this repo is Vigilant only):
  15087c3 ckpt 541: MP3-flasher side job closed: diagnosis + workaround steps sent to Tj; no rebui
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
