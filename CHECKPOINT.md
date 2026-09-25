# CHECKPOINT 365 — read me first, then TASKS.md

**Written:** 2026-09-25T14:50:46Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `8200ee0` (this checkpoint is the commit after it)

## Just done
K1/K4 app wiring compiles: AppContainer uses FileApiKeyStore (api_keys.json) + one-time migration from EncryptedApiKeyStore, UsageMeter (usage.json) shared by all clients, KeyPools read current keys; VM addKey/removeKey/moveKeyUp per provider, export/import via SAF, usage flow in UiState, flush after scan; Settings: API usage meters at top, key list editors under Pinnacle and Odds API (reorder, remove, ToS warning for >1 pinnapi key), Keys backup export/import; feed UsageStrip; backup rules exclude only the hardware-bound Novig connection

## Do this next
Screenshots + tests for meters (SampleScan usage state), UsageViews unit test, then K6 full tests

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/src/main/AndroidManifest.xml
    ?? app/src/main/res/xml/

## Last ten checkpoints
```
  e9f0235 ckpt 364: K3 tests green: data 134 tests (0 fail). New UsageMeterTest (13: month/day per
  4c7e396 ckpt 363: K1/K3 data layer (build compiles, tests not yet updated): FileApiKeyStore (pla
  d5a5da4 ckpt 362: K2 research recorded: RESEARCH.md §12 (Odds API resets 1st of month, cost=mar
  84bddf3 ckpt 361: Logged Tj's 2026-09-25 ~14:00Z request (keys survive updates, usage meters, mu
  f68c0c6 ckpt 360: SHIPPED v0.6.0 (code 10): CI 36141456836 green, release 36141782030 green, 4.6
  4eebbd6 ckpt 359: pre-release: v0.6.0: manual-only scans (Scan button / pull to refresh, nothing
  5065c28 ckpt 358: Docs for v0.6.0: BRIEF (manual-only + multi-source locked decisions), CLAUDE.m
  28997ab ckpt 357: v0.6.0 code complete: full ./gradlew test 173 pass/0 fail (2 live skipped); re
  cd87838 ckpt 356: v0.6.0 app layer compiles + 15 screenshot/UI tests green: VM manual scan() wit
  14ff2f1 ckpt 355: v0.6.0 data tests green (156, 0 fail): ScannerTest rewritten for manual scan (
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
