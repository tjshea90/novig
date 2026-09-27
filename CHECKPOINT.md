# CHECKPOINT 512 — read me first, then TASKS.md

**Written:** 2026-09-27T16:55:08Z · **tests:** all 1 fast checks green
**Branch:** `claude/vigilant-testing-odds-apis-mpvkc7` · **builds on:** `0235390` (this checkpoint is the commit after it)

## Just done
Wrote Tj's second request (no stale sportsbook odds in any comparison; after T1-T4) into TASKS.md U1-U4

## Do this next
T1: PropLineClient asks for novig in the same call, splits it into RefSnapshot.novigQuotes

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  2abdfb9 ckpt 511: Wrote Tj's request (PropLine's Novig prices order Novig reads, fallback to ori
  c15cb5c ckpt 510: SHIPPED v0.16.3 code 30 (S1-S5 ticked): https://github.com/tjshea90/novig/rele
  e46361a ckpt 509: pre-release: v0.16.3: PropLine first for sportsbook odds, The Odds API only as
  51e51af ckpt 508: S1-S4 ticked: fallback chain built and tested, full-test fixes done, floor 567
  c066f28 ckpt 507: S4 sweep fixes 3-4: player-roster lane gated on CNO on (UiState.cnoTeamRows; w
  6663b09 ckpt 506: S4 sweep fix 2: a fallback standing by drops its own older snapshot (Odds API'
  87963fb ckpt 505: S4 sweep fix 1: PlacedIndex league-aware same-game window (MLB 2h: doubleheade
  c148cd7 ckpt 504: S3 core built: ReferenceSource.fallbackFor/needed + ScanContext.covered/firstA
  82d5c33 ckpt 503: S1+S2: API map (books, quotas, freshness measured) and order decision written 
  fd13532 ckpt 502: Wrote Tj's 15:59Z request (full tests; best API first per book, others as auto
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
