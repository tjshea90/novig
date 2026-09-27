# CHECKPOINT 500 — read me first, then TASKS.md

**Written:** 2026-09-27T15:41:50Z · **tests:** all 1 fast checks green
**Branch:** `claude/vigilant-testing-odds-apis-mpvkc7` · **builds on:** `707adf5` (this checkpoint is the commit after it)

## Just done
pre-release: v0.16.2: placed bets hidden app-wide (either scanner, tracker too); Vigilant's bet sheet opens the exact bet slip; floating widget in every mode with CNO only/Both/Vigilant switch; Tracker leaves bets over ±6% EV out of every stat (versionCode 29, v0.16.2)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.16.2), then run: bash tools/record-release.sh v0.16.2 29 "v0.16.2: placed bets hidden app-wide (either scanner, tracker too); Vigilant's bet sheet opens the exact bet slip; floating widget in every mode with CNO only/Both/Vigilant switch; Tracker leaves bets over ±6% EV out of every stat"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  707adf5 ckpt 499: R1-R4, O1-O2 done and ticked: floor 549 tests 0 failures (8 skipped live), ass
  4a93c14 ckpt 498: O1 outlier rule (BetTracker.OUTLIER_EV=0.06, stats exclude, isOutlier) + tests
  08b797d ckpt 497: Fixed PlacedEverywhereTest expectation (sample tracker already hides Dallas ML
  bc2a094 ckpt 496: R2-R4 code written (PlacedIndex app-wide hiding, exact bet slip from sheet, fl
  800fb36 ckpt 495: Wrote Tj's 15:10Z request (placed bets hidden app-wide; Vigilant widget + exac
  0059052 ckpt 494: Released v0.16.1 (code 28): PropLine null-tolerant decoding + readable scan er
  94990e8 ckpt 493: pre-release: v0.16.1: fixes 'PropLine props … Unexpected JSON token … book
  bff826a ckpt 492: Q1 fixed: PropLine null-tolerant decoding (3 failing-first tests) + readable s
  9babee4 ckpt 491: Wrote Tj's 14:45Z screenshot request (PropLine props JSON error) into TASKS Q1
  b9e3c07 ckpt 490: Answered Tj (no code change): PinnWire setup prompt not needed (use own free k
```
