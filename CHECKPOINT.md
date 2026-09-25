# CHECKPOINT 377 — read me first, then TASKS.md

**Written:** 2026-09-25T15:53:09Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `18bf9df` (this checkpoint is the commit after it)

## Just done
A3/A4 + tests green: data 147 (0 fail), app 19. New AltMarketsTest (12: player-name matching, prop pricing same player/stat/line only, props cap, team total with swapped feed, 1H never vs full game, F5 label, budget keeps ML/open bets over props, league order + removals, settings migration, Kalshi props/TT/1H parse, pinnapi num_1 + team totals); fixed chip order (no selected-first), props-per-game + max-books settings

## Do this next
Live scan with alt markets (NFL/MLB via Kalshi) to verify real matching + timing; screenshots; version 0.8.0 code 12; docs; CI; ship; report

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6911ea9 ckpt 376: A1+A3 core compiles: leagues NFL,NCAAF,MLB,WNBA,NHL first, soccer/CFL/KBO/NPB 
  0fee52f ckpt 375: A2 research recorded: RESEARCH.md §13 (Novig alt market types + shapes per le
  37e727b ckpt 374: Logged Tj's 2026-09-25 ~15:20Z request (alt markets for NFL/NCAAF/MLB/WNBA, le
  8c4a9a6 ckpt 373: SHIPPED v0.7.0 (code 11): CI 36151518252 green, release 36151929213 green, 4.7
  9d2ba57 ckpt 372: pre-release: v0.7.0: API keys saved as a plain file (kept through updates, in 
  64c7ffa ckpt 371: Full tests complete: 190 tests pass (0 fail, 2 live skipped), release APK buil
  0e3479b ckpt 370: Full test UI improvements: feed sort Best EV / Soonest (FeedSort in settings, 
  b5c101d ckpt 369: Full test fix 2: open bets' markets are pinned past the per-game line cap (Pla
  7c96000 ckpt 368: Full test fix 1: a Novig key whose Keystore entry is gone (restore to a new ph
  0ffe277 ckpt 367: TASKS K1-K5 ticked with named tests
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
