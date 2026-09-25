# CHECKPOINT 366 — read me first, then TASKS.md

**Written:** 2026-09-25T14:54:03Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `47cf82d` (this checkpoint is the commit after it)

## Just done
K1-K5 done: keys in api_keys.json (migrated, backed up, export/import), UsageMeter ledger + KeyPool rotation for Odds API and pinnapi (multi keys, reorder), meters in Settings (per provider/key: used/limit, left, in use/next/spent/waiting/refused, resets) and a strip on the feed; status line no longer shows a single key's credits. Tests: data (UsageMeterTest 14, FileApiKeyStoreTest 3, client tests) + app UI test theMetersShowWhatsLeftPerKeyAndWhichKeyIsInUse, all green

## Do this next
K6 full tests: automated floor, then sweep every tab/subsystem (efficiency, caching, correctness vs OddsJam, UI), fix with failing-first tests, ship v0.7.0 code 11

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/src/main/kotlin/com/tjshea/vigilant/app/MainViewModel.kt
     M app/src/test/kotlin/com/tjshea/vigilant/app/SampleScan.kt

## Last ten checkpoints
```
  5b1bd2a ckpt 365: K1/K4 app wiring compiles: AppContainer uses FileApiKeyStore (api_keys.json) +
  e9f0235 ckpt 364: K3 tests green: data 134 tests (0 fail). New UsageMeterTest (13: month/day per
  4c7e396 ckpt 363: K1/K3 data layer (build compiles, tests not yet updated): FileApiKeyStore (pla
  d5a5da4 ckpt 362: K2 research recorded: RESEARCH.md §12 (Odds API resets 1st of month, cost=mar
  84bddf3 ckpt 361: Logged Tj's 2026-09-25 ~14:00Z request (keys survive updates, usage meters, mu
  f68c0c6 ckpt 360: SHIPPED v0.6.0 (code 10): CI 36141456836 green, release 36141782030 green, 4.6
  4eebbd6 ckpt 359: pre-release: v0.6.0: manual-only scans (Scan button / pull to refresh, nothing
  5065c28 ckpt 358: Docs for v0.6.0: BRIEF (manual-only + multi-source locked decisions), CLAUDE.m
  28997ab ckpt 357: v0.6.0 code complete: full ./gradlew test 173 pass/0 fail (2 live skipped); re
  cd87838 ckpt 356: v0.6.0 app layer compiles + 15 screenshot/UI tests green: VM manual scan() wit
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
