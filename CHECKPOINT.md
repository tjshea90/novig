# CHECKPOINT 509 — read me first, then TASKS.md

**Written:** 2026-09-27T16:38:49Z · **tests:** all 1 fast checks green
**Branch:** `claude/vigilant-testing-odds-apis-mpvkc7` · **builds on:** `51e51af` (this checkpoint is the commit after it)

## Just done
pre-release: v0.16.3: PropLine first for sportsbook odds, The Odds API only as automatic backup (lines and props); stale backup quotes dropped; placed-bet matching handles doubleheaders and series; full-test fixes (versionCode 30, v0.16.3)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.16.3), then run: bash tools/record-release.sh v0.16.3 30 "v0.16.3: PropLine first for sportsbook odds, The Odds API only as automatic backup (lines and props); stale backup quotes dropped; placed-bet matching handles doubleheaders and series; full-test fixes"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  51e51af ckpt 508: S1-S4 ticked: fallback chain built and tested, full-test fixes done, floor 567
  c066f28 ckpt 507: S4 sweep fixes 3-4: player-roster lane gated on CNO on (UiState.cnoTeamRows; w
  6663b09 ckpt 506: S4 sweep fix 2: a fallback standing by drops its own older snapshot (Odds API'
  87963fb ckpt 505: S4 sweep fix 1: PlacedIndex league-aware same-game window (MLB 2h: doubleheade
  c148cd7 ckpt 504: S3 core built: ReferenceSource.fallbackFor/needed + ScanContext.covered/firstA
  82d5c33 ckpt 503: S1+S2: API map (books, quotas, freshness measured) and order decision written 
  fd13532 ckpt 502: Wrote Tj's 15:59Z request (full tests; best API first per book, others as auto
  30debdd ckpt 501: SHIPPED v0.16.2 code 29 (R1-R5, O1-O3 all ticked): https://github.com/tjshea90
  40cacdd ckpt 500: pre-release: v0.16.2: placed bets hidden app-wide (either scanner, tracker too
  707adf5 ckpt 499: R1-R4, O1-O2 done and ticked: floor 549 tests 0 failures (8 skipped live), ass
```
