# CHECKPOINT 507 — read me first, then TASKS.md

**Written:** 2026-09-27T16:26:35Z · **tests:** all 1 fast checks green
**Branch:** `claude/vigilant-testing-odds-apis-mpvkc7` · **builds on:** `c5615f7` (this checkpoint is the commit after it)

## Just done
S4 sweep fixes 3-4: player-roster lane gated on CNO on (UiState.cnoTeamRows; widget in Vigilant-only mode kept ESPN reads going for an old CNO list), Odds API backup stands by if its free list can't be read (no false error banner). CLAUDE.md surface list notes the fallback chain

## Do this next
Run full floor with screenshots + assembleRelease, check PNGs (settings sources, feed), then tick S1-S4, bump v0.16.3 code 30, ship

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M CLAUDE.md

## Last ten checkpoints
```
  6663b09 ckpt 506: S4 sweep fix 2: a fallback standing by drops its own older snapshot (Odds API'
  87963fb ckpt 505: S4 sweep fix 1: PlacedIndex league-aware same-game window (MLB 2h: doubleheade
  c148cd7 ckpt 504: S3 core built: ReferenceSource.fallbackFor/needed + ScanContext.covered/firstA
  82d5c33 ckpt 503: S1+S2: API map (books, quotas, freshness measured) and order decision written 
  fd13532 ckpt 502: Wrote Tj's 15:59Z request (full tests; best API first per book, others as auto
  30debdd ckpt 501: SHIPPED v0.16.2 code 29 (R1-R5, O1-O3 all ticked): https://github.com/tjshea90
  40cacdd ckpt 500: pre-release: v0.16.2: placed bets hidden app-wide (either scanner, tracker too
  707adf5 ckpt 499: R1-R4, O1-O2 done and ticked: floor 549 tests 0 failures (8 skipped live), ass
  4a93c14 ckpt 498: O1 outlier rule (BetTracker.OUTLIER_EV=0.06, stats exclude, isOutlier) + tests
  08b797d ckpt 497: Fixed PlacedEverywhereTest expectation (sample tracker already hides Dallas ML
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
