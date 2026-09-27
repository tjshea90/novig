# CHECKPOINT 504 — read me first, then TASKS.md

**Written:** 2026-09-27T16:13:41Z · **tests:** all 1 fast checks green
**Branch:** `claude/vigilant-testing-odds-apis-mpvkc7` · **builds on:** `c966937` (this checkpoint is the commit after it)

## Just done
S3 core built: ReferenceSource.fallbackFor/needed + ScanContext.covered/firstAnswered; Scanner waits fallbacks on their first source (covering()); Odds API game lines behind PropLine (needed rule: unanswered league, sharp-only book, Novig game missing but listed free); Odds API props skip PropLine-priced game/stat; Settings copy + 'on standby' summary. data tests 370/0 incl. 9 new

## Do this next
Failing-first proof for the props/game-line fallback (old code spends credits), app tests + screenshots, then S4 full-test sweep

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  82d5c33 ckpt 503: S1+S2: API map (books, quotas, freshness measured) and order decision written 
  fd13532 ckpt 502: Wrote Tj's 15:59Z request (full tests; best API first per book, others as auto
  30debdd ckpt 501: SHIPPED v0.16.2 code 29 (R1-R5, O1-O3 all ticked): https://github.com/tjshea90
  40cacdd ckpt 500: pre-release: v0.16.2: placed bets hidden app-wide (either scanner, tracker too
  707adf5 ckpt 499: R1-R4, O1-O2 done and ticked: floor 549 tests 0 failures (8 skipped live), ass
  4a93c14 ckpt 498: O1 outlier rule (BetTracker.OUTLIER_EV=0.06, stats exclude, isOutlier) + tests
  08b797d ckpt 497: Fixed PlacedEverywhereTest expectation (sample tracker already hides Dallas ML
  bc2a094 ckpt 496: R2-R4 code written (PlacedIndex app-wide hiding, exact bet slip from sheet, fl
  800fb36 ckpt 495: Wrote Tj's 15:10Z request (placed bets hidden app-wide; Vigilant widget + exac
  0059052 ckpt 494: Released v0.16.1 (code 28): PropLine null-tolerant decoding + readable scan er
```

(11 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
