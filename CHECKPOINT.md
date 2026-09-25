# CHECKPOINT 385 — read me first, then TASKS.md

**Written:** 2026-09-25T16:44:16Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `b3e5dfb` (this checkpoint is the commit after it)

## Just done
P2-P4 tests: OddsApiPropsTest (10: parsing styles, Yes/No, no alternates, free events + metered props, window/soonest/budget across leagues, props-off, per-game reuse, partial failure, market-average pricing across name styles), PlayerNamesTest (4, 3 confirmed failing on old matcher), ScannerTest partial+catalog, CreditEstimateTest (fixed 6-vs-3 family bug), settings screenshot extended; full suite 217 green exit 0

## Do this next
Docs: RESEARCH §14, BRIEF/CLAUDE surface, TASKS P1-P5 tick; bump 0.9.0 code 13; full suite; ship; release; report to Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  f4da200 ckpt 384: P2/P4/P5 wiring: PlayerNames (nicknames incl. Hollywood/Marquise, Last-First, 
  9ac808a ckpt 383: P2 core: TheOddsApiClient.events() (free) + eventOdds() + prop parsing (Over/U
  062cf9d ckpt 382: P2 in progress: ScanContext/needsCatalog in ReferenceSource, Scanner waits for
  e812ace ckpt 381: Logged Tj's 2026-09-25 ~16:15Z request (sportsbook props, market average devig
  09d5cbe ckpt 380: SHIPPED v0.8.0 (code 12): CI 36158016075 green, release 36158521051 green, 4.7
  2b65e01 ckpt 379: pre-release: v0.8.0: leagues ordered NFL, NCAAF, MLB, WNBA, NHL first; soccer,
  db386e6 ckpt 378: v0.8.0 ready: 200 tests pass (0 fail, 2 live skipped), release APK builds; doc
  5862aea ckpt 377: A3/A4 + tests green: data 147 (0 fail), app 19. New AltMarketsTest (12: player
  6911ea9 ckpt 376: A1+A3 core compiles: leagues NFL,NCAAF,MLB,WNBA,NHL first, soccer/CFL/KBO/NPB 
  0fee52f ckpt 375: A2 research recorded: RESEARCH.md §13 (Novig alt market types + shapes per le
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
