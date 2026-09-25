# CHECKPOINT 379 — read me first, then TASKS.md

**Written:** 2026-09-25T16:05:23Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `7c45a5c` (this checkpoint is the commit after it)

## Just done
pre-release: v0.8.0: leagues ordered NFL, NCAAF, MLB, WNBA, NHL first; soccer, CFL, KBO, NPB removed; alternative markets priced against Kalshi and Pinnacle: 1st-half/F5 spreads and totals, team totals, NFL/MLB/WNBA player props (same player, stat and line); per-game prop cap and a per-scan budget keep scans fast; Kalshi paced to avoid its throttling. 200 tests; live NFL 14/16 and MLB 17/20 games matched with props priced. (versionCode 12, v0.8.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.8.0), then run: bash tools/record-release.sh v0.8.0 12 "v0.8.0: leagues ordered NFL, NCAAF, MLB, WNBA, NHL first; soccer, CFL, KBO, NPB removed; alternative markets priced against Kalshi and Pinnacle: 1st-half/F5 spreads and totals, team totals, NFL/MLB/WNBA player props (same player, stat and line); per-game prop cap and a per-scan budget keep scans fast; Kalshi paced to avoid its throttling. 200 tests; live NFL 14/16 and MLB 17/20 games matched with props priced."

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  db386e6 ckpt 378: v0.8.0 ready: 200 tests pass (0 fail, 2 live skipped), release APK builds; doc
  5862aea ckpt 377: A3/A4 + tests green: data 147 (0 fail), app 19. New AltMarketsTest (12: player
  6911ea9 ckpt 376: A1+A3 core compiles: leagues NFL,NCAAF,MLB,WNBA,NHL first, soccer/CFL/KBO/NPB 
  0fee52f ckpt 375: A2 research recorded: RESEARCH.md §13 (Novig alt market types + shapes per le
  37e727b ckpt 374: Logged Tj's 2026-09-25 ~15:20Z request (alt markets for NFL/NCAAF/MLB/WNBA, le
  8c4a9a6 ckpt 373: SHIPPED v0.7.0 (code 11): CI 36151518252 green, release 36151929213 green, 4.7
  9d2ba57 ckpt 372: pre-release: v0.7.0: API keys saved as a plain file (kept through updates, in 
  64c7ffa ckpt 371: Full tests complete: 190 tests pass (0 fail, 2 live skipped), release APK buil
  0e3479b ckpt 370: Full test UI improvements: feed sort Best EV / Soonest (FeedSort in settings, 
  b5c101d ckpt 369: Full test fix 2: open bets' markets are pinned past the per-game line cap (Pla
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
