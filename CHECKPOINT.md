# CHECKPOINT 549 — read me first, then TASKS.md

**Written:** 2026-09-28T03:55:51Z · **tests:** all 1 fast checks green
**Branch:** `claude/scan-perf-background-notifications-ig8u0c` · **builds on:** `16888bc` (this checkpoint is the commit after it)

## Just done
P6 full tests: fix F1 duplicate alerts when two scans end together (send mutex; AutoScanTest 'two scans ending at once' fails pre-fix 4 vs 2), F2 notification permission asked once per auto-scan turn-on (source pin)

## Do this next
P6: live smoke tests, then sweep rest of app + screenshots + release build

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  45fbbf7 ckpt 548: P1-P5 done and ticked: floor 654 tests, 0 failures, 8 live skipped (engine/dat
  3b700a0 ckpt 547: P4/P5 tests: AutoScanTest 13, AgreementTest 3, AlertLogTest 3, NovigLive.readN
  25a8521 ckpt 546: P4/P5 code written, compiles: AutoScanService (specialUse FGS, exact alarms, b
  8cb689e ckpt 545: P1+P2 done, P3 mostly: prices/scan to 1200, props/game 16/24, odds cap +120/+1
  6c91990 ckpt 544: Recorded Tj's 03:19Z request as P1-P6 in TASKS.md
  5f220e0 ckpt 543: Released Vigilant v0.17.1 (code 33): release.yml run 36365476060 green, tag v0
  a0a82b7 ckpt 542: Removed an unrelated side job from this repo (Tj: this repo is Vigilant only):
  15087c3 ckpt 541: MP3-flasher side job closed: diagnosis + workaround steps sent to Tj; no rebui
  2bdcfe8 ckpt 540: Recorded Tj's MP3-flasher side job in TASKS.md (M1-M3); diagnosis: 32-bit-only
  b6a37e6 ckpt 539: PAUSED for Tj's model switch. H1-H4 all done and on main (v0.17.1 code 33 gate
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
