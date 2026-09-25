# CHECKPOINT 376 — read me first, then TASKS.md

**Written:** 2026-09-25T15:49:30Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `6ad46e5` (this checkpoint is the commit after it)

## Just done
A1+A3 core compiles: leagues NFL,NCAAF,MLB,WNBA,NHL first, soccer/CFL/KBO/NPB removed (+ settings migration schema 3); model LineKind TEAM_TOTAL/PLAYER_PROP, RefBookMarket period/subject/stat, LineKey.matches (player names via PlayerNames); MarketFamily FIRST_HALF/TEAM_TOTAL/PLAYER_PROPS (PropStats maps Novig types <-> Kalshi series); Planner parses SPREAD_1H/TOTAL_1H/TEAM_TOTAL/props, groups caps, propsPerGame, maxBooksPerScan budget; Kalshi parses 1H/F5, team totals, props, paced 2/s with 429 backoff + partial results; pinnapi num_1 + team totals; Scanner fetches only selected families; 3-way soccer code removed

## Do this next
Settings UI (props per game, max books per scan), fix tests (remove soccer/3-way, NovigTextTest, LiveNovigSmokeTest), new tests (props/team total/1H planning+pricing, PlayerNames, Kalshi prop/TT/F5 parse, pinnapi periods/TT, migration, budget, league order), screenshots, live scan, ship v0.8.0 code 12

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  0fee52f ckpt 375: A2 research recorded: RESEARCH.md §13 (Novig alt market types + shapes per le
  37e727b ckpt 374: Logged Tj's 2026-09-25 ~15:20Z request (alt markets for NFL/NCAAF/MLB/WNBA, le
  8c4a9a6 ckpt 373: SHIPPED v0.7.0 (code 11): CI 36151518252 green, release 36151929213 green, 4.7
  9d2ba57 ckpt 372: pre-release: v0.7.0: API keys saved as a plain file (kept through updates, in 
  64c7ffa ckpt 371: Full tests complete: 190 tests pass (0 fail, 2 live skipped), release APK buil
  0e3479b ckpt 370: Full test UI improvements: feed sort Best EV / Soonest (FeedSort in settings, 
  b5c101d ckpt 369: Full test fix 2: open bets' markets are pinned past the per-game line cap (Pla
  7c96000 ckpt 368: Full test fix 1: a Novig key whose Keystore entry is gone (restore to a new ph
  0ffe277 ckpt 367: TASKS K1-K5 ticked with named tests
  055fba1 ckpt 366: K1-K5 done: keys in api_keys.json (migrated, backed up, export/import), UsageM
```

(12 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
