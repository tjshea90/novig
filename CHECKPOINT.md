# CHECKPOINT 368 — read me first, then TASKS.md

**Written:** 2026-09-25T14:56:21Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `9e0267f` (this checkpoint is the commit after it)

## Just done
Full test fix 1: a Novig key whose Keystore entry is gone (restore to a new phone) made signing throw IllegalStateException, failing the whole price read; now it falls back to public prices with a 'connect it again' message. Test 'a key that can't sign … falls back to public prices' failed before the fix, passes after

## Do this next
Fix 2: pin tracked (pending) bets' markets past the per-game line cap so CLV keeps updating

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  0ffe277 ckpt 367: TASKS K1-K5 ticked with named tests
  055fba1 ckpt 366: K1-K5 done: keys in api_keys.json (migrated, backed up, export/import), UsageM
  5b1bd2a ckpt 365: K1/K4 app wiring compiles: AppContainer uses FileApiKeyStore (api_keys.json) +
  e9f0235 ckpt 364: K3 tests green: data 134 tests (0 fail). New UsageMeterTest (13: month/day per
  4c7e396 ckpt 363: K1/K3 data layer (build compiles, tests not yet updated): FileApiKeyStore (pla
  d5a5da4 ckpt 362: K2 research recorded: RESEARCH.md §12 (Odds API resets 1st of month, cost=mar
  84bddf3 ckpt 361: Logged Tj's 2026-09-25 ~14:00Z request (keys survive updates, usage meters, mu
  f68c0c6 ckpt 360: SHIPPED v0.6.0 (code 10): CI 36141456836 green, release 36141782030 green, 4.6
  4eebbd6 ckpt 359: pre-release: v0.6.0: manual-only scans (Scan button / pull to refresh, nothing
  5065c28 ckpt 358: Docs for v0.6.0: BRIEF (manual-only + multi-source locked decisions), CLAUDE.m
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
