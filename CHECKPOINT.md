# CHECKPOINT 375 — read me first, then TASKS.md

**Written:** 2026-09-25T15:37:16Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `37e727b` (this checkpoint is the commit after it)

## Just done
A2 research recorded: RESEARCH.md §13 (Novig alt market types + shapes per league; Kalshi series for 1H/F5 spread+total, team totals, player props with shapes; pinnapi num_1 + team_total; Polymarket/Odds API not usable; 1H moneylines skipped (3-way vs 2-way FMV); Kalshi anonymous 429 after ~130 req/40s -> pace 2/s)

## Do this next
Implement A1 (league order/removal + settings migration) then A3 model: LineKind TEAM_TOTAL/PLAYER_PROP, RefBookMarket period/subject/stat, MarketFamily FIRST_HALF/TEAM_TOTAL/PLAYER_PROPS, Planner parsing + caps + per-scan book budget, Kalshi parsers + pacing, pinnapi num_1/team_total

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M RESEARCH.md

## Last ten checkpoints
```
  37e727b ckpt 374: Logged Tj's 2026-09-25 ~15:20Z request (alt markets for NFL/NCAAF/MLB/WNBA, le
  8c4a9a6 ckpt 373: SHIPPED v0.7.0 (code 11): CI 36151518252 green, release 36151929213 green, 4.7
  9d2ba57 ckpt 372: pre-release: v0.7.0: API keys saved as a plain file (kept through updates, in 
  64c7ffa ckpt 371: Full tests complete: 190 tests pass (0 fail, 2 live skipped), release APK buil
  0e3479b ckpt 370: Full test UI improvements: feed sort Best EV / Soonest (FeedSort in settings, 
  b5c101d ckpt 369: Full test fix 2: open bets' markets are pinned past the per-game line cap (Pla
  7c96000 ckpt 368: Full test fix 1: a Novig key whose Keystore entry is gone (restore to a new ph
  0ffe277 ckpt 367: TASKS K1-K5 ticked with named tests
  055fba1 ckpt 366: K1-K5 done: keys in api_keys.json (migrated, backed up, export/import), UsageM
  5b1bd2a ckpt 365: K1/K4 app wiring compiles: AppContainer uses FileApiKeyStore (api_keys.json) +
```
