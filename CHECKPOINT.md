# CHECKPOINT 505 — read me first, then TASKS.md

**Written:** 2026-09-27T16:21:31Z · **tests:** all 1 fast checks green
**Branch:** `claude/vigilant-testing-odds-apis-mpvkc7` · **builds on:** `fafe09a` (this checkpoint is the commit after it)

## Just done
S4 sweep fix 1: PlacedIndex league-aware same-game window (MLB 2h: doubleheader game 2 no longer hidden by a game-1 mark; football 12h) and unknown start times match only marks from the last day (old marks without a start hid later games in a series). League carried on MiniWindow.Item/PlacedBet. Proof: both checks FAIL on v0.16.2's PlacedIndex, pass now (PlacedIndexTest 7/7). Also fixed settings props-estimate test for the PropLine-backup wording

## Do this next
Continue S4 sweep (network efficiency, data retention, battery, stale copy), then full floor + screenshots

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  c148cd7 ckpt 504: S3 core built: ReferenceSource.fallbackFor/needed + ScanContext.covered/firstA
  82d5c33 ckpt 503: S1+S2: API map (books, quotas, freshness measured) and order decision written 
  fd13532 ckpt 502: Wrote Tj's 15:59Z request (full tests; best API first per book, others as auto
  30debdd ckpt 501: SHIPPED v0.16.2 code 29 (R1-R5, O1-O3 all ticked): https://github.com/tjshea90
  40cacdd ckpt 500: pre-release: v0.16.2: placed bets hidden app-wide (either scanner, tracker too
  707adf5 ckpt 499: R1-R4, O1-O2 done and ticked: floor 549 tests 0 failures (8 skipped live), ass
  4a93c14 ckpt 498: O1 outlier rule (BetTracker.OUTLIER_EV=0.06, stats exclude, isOutlier) + tests
  08b797d ckpt 497: Fixed PlacedEverywhereTest expectation (sample tracker already hides Dallas ML
  bc2a094 ckpt 496: R2-R4 code written (PlacedIndex app-wide hiding, exact bet slip from sheet, fl
  800fb36 ckpt 495: Wrote Tj's 15:10Z request (placed bets hidden app-wide; Vigilant widget + exac
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
