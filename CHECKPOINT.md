# CHECKPOINT 372 — read me first, then TASKS.md

**Written:** 2026-09-25T15:06:07Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `34b7aa2` (this checkpoint is the commit after it)

## Just done
pre-release: v0.7.0: API keys saved as a plain file (kept through updates, in Android backup, export/import), moved over from the old encrypted store; several keys per provider (The Odds API, Pinnacle/pinnapi) rotated by a persistent usage ledger: server headers or local counts, skips a key before it runs out, rests spent keys until the provider's reset (1st of month / midnight UTC) and starts again at key 1; usage meters in Settings and on the feed, updated after every call; full-test fixes: broken Novig key falls back to public prices, open bets' lines always priced for CLV, stale-scan warning, Best EV/Soonest sort. 190 tests. (versionCode 11, v0.7.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.7.0), then run: bash tools/record-release.sh v0.7.0 11 "v0.7.0: API keys saved as a plain file (kept through updates, in Android backup, export/import), moved over from the old encrypted store; several keys per provider (The Odds API, Pinnacle/pinnapi) rotated by a persistent usage ledger: server headers or local counts, skips a key before it runs out, rests spent keys until the provider's reset (1st of month / midnight UTC) and starts again at key 1; usage meters in Settings and on the feed, updated after every call; full-test fixes: broken Novig key falls back to public prices, open bets' lines always priced for CLV, stale-scan warning, Best EV/Soonest sort. 190 tests."

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  64c7ffa ckpt 371: Full tests complete: 190 tests pass (0 fail, 2 live skipped), release APK buil
  0e3479b ckpt 370: Full test UI improvements: feed sort Best EV / Soonest (FeedSort in settings, 
  b5c101d ckpt 369: Full test fix 2: open bets' markets are pinned past the per-game line cap (Pla
  7c96000 ckpt 368: Full test fix 1: a Novig key whose Keystore entry is gone (restore to a new ph
  0ffe277 ckpt 367: TASKS K1-K5 ticked with named tests
  055fba1 ckpt 366: K1-K5 done: keys in api_keys.json (migrated, backed up, export/import), UsageM
  5b1bd2a ckpt 365: K1/K4 app wiring compiles: AppContainer uses FileApiKeyStore (api_keys.json) +
  e9f0235 ckpt 364: K3 tests green: data 134 tests (0 fail). New UsageMeterTest (13: month/day per
  4c7e396 ckpt 363: K1/K3 data layer (build compiles, tests not yet updated): FileApiKeyStore (pla
  d5a5da4 ckpt 362: K2 research recorded: RESEARCH.md §12 (Odds API resets 1st of month, cost=mar
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
