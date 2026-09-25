# CHECKPOINT 370 — read me first, then TASKS.md

**Written:** 2026-09-25T14:58:47Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `1046858` (this checkpoint is the commit after it)

## Just done
Full test UI improvements: feed sort Best EV / Soonest (FeedSort in settings, Pricing.feed), summary split into counts + fair-source line, stale-scan banner (>10 min) with Scan action. Tests: PlannerPricingTest 'feed can be ordered by start time', ScreenshotTest anOldScanWarnsBeforeBetting…, theFeedCanBeSortedBySoonest; app 19/19, data green

## Do this next
Look at screenshots (feed, settings), full regression ./gradlew test with XML check, docs (RESEARCH/NOVIG_API/BRIEF/CLAUDE), bump v0.7.0 code 11, CI, ship, report

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  b5c101d ckpt 369: Full test fix 2: open bets' markets are pinned past the per-game line cap (Pla
  7c96000 ckpt 368: Full test fix 1: a Novig key whose Keystore entry is gone (restore to a new ph
  0ffe277 ckpt 367: TASKS K1-K5 ticked with named tests
  055fba1 ckpt 366: K1-K5 done: keys in api_keys.json (migrated, backed up, export/import), UsageM
  5b1bd2a ckpt 365: K1/K4 app wiring compiles: AppContainer uses FileApiKeyStore (api_keys.json) +
  e9f0235 ckpt 364: K3 tests green: data 134 tests (0 fail). New UsageMeterTest (13: month/day per
  4c7e396 ckpt 363: K1/K3 data layer (build compiles, tests not yet updated): FileApiKeyStore (pla
  d5a5da4 ckpt 362: K2 research recorded: RESEARCH.md §12 (Odds API resets 1st of month, cost=mar
  84bddf3 ckpt 361: Logged Tj's 2026-09-25 ~14:00Z request (keys survive updates, usage meters, mu
  f68c0c6 ckpt 360: SHIPPED v0.6.0 (code 10): CI 36141456836 green, release 36141782030 green, 4.6
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
