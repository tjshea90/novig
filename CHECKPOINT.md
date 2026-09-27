# CHECKPOINT 498 — read me first, then TASKS.md

**Written:** 2026-09-27T15:29:56Z · **tests:** all 1 fast checks green
**Branch:** `claude/vigilant-testing-odds-apis-mpvkc7` · **builds on:** `2193b0a` (this checkpoint is the commit after it)

## Just done
O1 outlier rule (BetTracker.OUTLIER_EV=0.06, stats exclude, isOutlier) + tests; O2 Tracker note/card tag/profit line + screenshot tests; R4 settings rescan chips in any mode + Vigilant-only settings/widget tests

## Do this next
Run full floor with -Pscreenshots + assembleRelease, check PNGs (9p, 4c), tick R1-R5/O1-O3, ship v0.16.2 code 29

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  08b797d ckpt 497: Fixed PlacedEverywhereTest expectation (sample tracker already hides Dallas ML
  bc2a094 ckpt 496: R2-R4 code written (PlacedIndex app-wide hiding, exact bet slip from sheet, fl
  800fb36 ckpt 495: Wrote Tj's 15:10Z request (placed bets hidden app-wide; Vigilant widget + exac
  0059052 ckpt 494: Released v0.16.1 (code 28): PropLine null-tolerant decoding + readable scan er
  94990e8 ckpt 493: pre-release: v0.16.1: fixes 'PropLine props … Unexpected JSON token … book
  bff826a ckpt 492: Q1 fixed: PropLine null-tolerant decoding (3 failing-first tests) + readable s
  9babee4 ckpt 491: Wrote Tj's 14:45Z screenshot request (PropLine props JSON error) into TASKS Q1
  b9e3c07 ckpt 490: Answered Tj (no code change): PinnWire setup prompt not needed (use own free k
  dabbe8c ckpt 489: Released v0.16.0 (code 27), recorded; release.yml text updated; P1-P5 ticked
  e4a3561 ckpt 488: pre-release: v0.16.0: Pinnacle player props (free PinnWire key) and 30 sportsb
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
