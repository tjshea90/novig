# CHECKPOINT 508 — read me first, then TASKS.md

**Written:** 2026-09-27T16:34:00Z · **tests:** all 1 fast checks green
**Branch:** `claude/vigilant-testing-odds-apis-mpvkc7` · **builds on:** `49f9dbb` (this checkpoint is the commit after it)

## Just done
S1-S4 ticked: fallback chain built and tested, full-test fixes done, floor 567/0, RESEARCH.md §23.4-23.5; version bumped to v0.16.3 code 30

## Do this next
Wait for CI green on head, ship.sh, trigger release.yml, confirm, record-release, tick S5, send link

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M RESEARCH.md
     M TASKS.md

## Last ten checkpoints
```
  c066f28 ckpt 507: S4 sweep fixes 3-4: player-roster lane gated on CNO on (UiState.cnoTeamRows; w
  6663b09 ckpt 506: S4 sweep fix 2: a fallback standing by drops its own older snapshot (Odds API'
  87963fb ckpt 505: S4 sweep fix 1: PlacedIndex league-aware same-game window (MLB 2h: doubleheade
  c148cd7 ckpt 504: S3 core built: ReferenceSource.fallbackFor/needed + ScanContext.covered/firstA
  82d5c33 ckpt 503: S1+S2: API map (books, quotas, freshness measured) and order decision written 
  fd13532 ckpt 502: Wrote Tj's 15:59Z request (full tests; best API first per book, others as auto
  30debdd ckpt 501: SHIPPED v0.16.2 code 29 (R1-R5, O1-O3 all ticked): https://github.com/tjshea90
  40cacdd ckpt 500: pre-release: v0.16.2: placed bets hidden app-wide (either scanner, tracker too
  707adf5 ckpt 499: R1-R4, O1-O2 done and ticked: floor 549 tests 0 failures (8 skipped live), ass
  4a93c14 ckpt 498: O1 outlier rule (BetTracker.OUTLIER_EV=0.06, stats exclude, isOutlier) + tests
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
