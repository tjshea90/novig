# CHECKPOINT 513 — read me first, then TASKS.md

**Written:** 2026-09-27T17:04:33Z · **tests:** all 1 fast checks green
**Branch:** `claude/vigilant-testing-odds-apis-mpvkc7` · **builds on:** `0bd4682` (this checkpoint is the commit after it)

## Just done
T1-T3 done: PropLine relays Novig's prices in the same calls (RefSnapshot.novig), Scanner.preview orders reads by EV at those prices, automatic fallback to the original order (no/failed/late/old relay, unquoted line). data 379/0 incl. NovigPreviewTest 5

## Do this next
Start U1 stale-odds audit (after T per Tj); ship T4 together with U4 at the end

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M RESEARCH.md
     M TASKS.md

## Last ten checkpoints
```
  b9a59a9 ckpt 512: Wrote Tj's second request (no stale sportsbook odds in any comparison; after T
  2abdfb9 ckpt 511: Wrote Tj's request (PropLine's Novig prices order Novig reads, fallback to ori
  c15cb5c ckpt 510: SHIPPED v0.16.3 code 30 (S1-S5 ticked): https://github.com/tjshea90/novig/rele
  e46361a ckpt 509: pre-release: v0.16.3: PropLine first for sportsbook odds, The Odds API only as
  51e51af ckpt 508: S1-S4 ticked: fallback chain built and tested, full-test fixes done, floor 567
  c066f28 ckpt 507: S4 sweep fixes 3-4: player-roster lane gated on CNO on (UiState.cnoTeamRows; w
  6663b09 ckpt 506: S4 sweep fix 2: a fallback standing by drops its own older snapshot (Odds API'
  87963fb ckpt 505: S4 sweep fix 1: PlacedIndex league-aware same-game window (MLB 2h: doubleheade
  c148cd7 ckpt 504: S3 core built: ReferenceSource.fallbackFor/needed + ScanContext.covered/firstA
  82d5c33 ckpt 503: S1+S2: API map (books, quotas, freshness measured) and order decision written 
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
