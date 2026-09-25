# CHECKPOINT 383 — read me first, then TASKS.md

**Written:** 2026-09-25T16:35:48Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `c7c4e0b` (this checkpoint is the commit after it)

## Just done
P2 core: TheOddsApiClient.events() (free) + eventOdds() + prop parsing (Over/Under by player, Yes/No -> Over/Under 0.5, one-sided dropped); PropStats per-sport verified keys (+player_tds); OddsApiPropsSource (needsCatalog, soonest-first budget across leagues, per-game reuse, only types Novig lists); PartialReferenceException kept by Scanner; data compiles

## Do this next
PlayerNames nicknames + Last, First; AppContainer wiring; Settings UI; tests (client parse, props source budget/reuse/match, PlayerNames); RESEARCH §14; v0.9.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  062cf9d ckpt 382: P2 in progress: ScanContext/needsCatalog in ReferenceSource, Scanner waits for
  e812ace ckpt 381: Logged Tj's 2026-09-25 ~16:15Z request (sportsbook props, market average devig
  09d5cbe ckpt 380: SHIPPED v0.8.0 (code 12): CI 36158016075 green, release 36158521051 green, 4.7
  2b65e01 ckpt 379: pre-release: v0.8.0: leagues ordered NFL, NCAAF, MLB, WNBA, NHL first; soccer,
  db386e6 ckpt 378: v0.8.0 ready: 200 tests pass (0 fail, 2 live skipped), release APK builds; doc
  5862aea ckpt 377: A3/A4 + tests green: data 147 (0 fail), app 19. New AltMarketsTest (12: player
  6911ea9 ckpt 376: A1+A3 core compiles: leagues NFL,NCAAF,MLB,WNBA,NHL first, soccer/CFL/KBO/NPB 
  0fee52f ckpt 375: A2 research recorded: RESEARCH.md §13 (Novig alt market types + shapes per le
  37e727b ckpt 374: Logged Tj's 2026-09-25 ~15:20Z request (alt markets for NFL/NCAAF/MLB/WNBA, le
  8c4a9a6 ckpt 373: SHIPPED v0.7.0 (code 11): CI 36151518252 green, release 36151929213 green, 4.7
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
