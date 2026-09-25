# CHECKPOINT 386 — read me first, then TASKS.md

**Written:** 2026-09-25T16:50:41Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `163b8de` (this checkpoint is the commit after it)

## Just done
Docs (RESEARCH §14, BRIEF sportsbook-props decision, CLAUDE surface list, TASKS P1-P4 ticked with named tests); Scanner fetches book-only Novig prop types only when book props are on (+test); live Novig/Kalshi scan clean; version 0.9.0 code 13

## Do this next
Full suite with exit code + XML; ship.sh; CI green; trigger release.yml; confirm release; record-release; report to Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  5eac253 ckpt 385: P2-P4 tests: OddsApiPropsTest (10: parsing styles, Yes/No, no alternates, free
  f4da200 ckpt 384: P2/P4/P5 wiring: PlayerNames (nicknames incl. Hollywood/Marquise, Last-First, 
  9ac808a ckpt 383: P2 core: TheOddsApiClient.events() (free) + eventOdds() + prop parsing (Over/U
  062cf9d ckpt 382: P2 in progress: ScanContext/needsCatalog in ReferenceSource, Scanner waits for
  e812ace ckpt 381: Logged Tj's 2026-09-25 ~16:15Z request (sportsbook props, market average devig
  09d5cbe ckpt 380: SHIPPED v0.8.0 (code 12): CI 36158016075 green, release 36158521051 green, 4.7
  2b65e01 ckpt 379: pre-release: v0.8.0: leagues ordered NFL, NCAAF, MLB, WNBA, NHL first; soccer,
  db386e6 ckpt 378: v0.8.0 ready: 200 tests pass (0 fail, 2 live skipped), release APK builds; doc
  5862aea ckpt 377: A3/A4 + tests green: data 147 (0 fail), app 19. New AltMarketsTest (12: player
  6911ea9 ckpt 376: A1+A3 core compiles: leagues NFL,NCAAF,MLB,WNBA,NHL first, soccer/CFL/KBO/NPB 
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
