# CHECKPOINT 364 — read me first, then TASKS.md

**Written:** 2026-09-25T14:46:44Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `be94503` (this checkpoint is the commit after it)

## Just done
K3 tests green: data 134 tests (0 fail). New UsageMeterTest (13: month/day periods, rotation from key 1, reset back to key 1 on the 1st, pre-emptive skip, 6h re-probe after a reset that didn't happen, billing-cycle follow, pinnapi per-minute + daily local counting, refused key, persistence, keyless daily counters, pool rotation + 'until Oct 1' message, burst wait), FileApiKeyStoreTest (3), TheOddsApiClientTest header->meter, skip-before-refused, invalid vs depleted; pinnapi multi-key 429 rotation. Fixed 2 ledger bugs the tests caught (re-probe stuck at remaining 0; burst cooldown)

## Do this next
App: migrate keys from EncryptedApiKeyStore to FileApiKeyStore (api_keys.json), AppContainer wiring (UsageMeter usage.json, KeyPools reading key store, usage passed to clients), VM: flush after scan, pinnapi multi keys, export/import via SAF; UI meters (Settings card + feed strip); screenshots

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  4c7e396 ckpt 363: K1/K3 data layer (build compiles, tests not yet updated): FileApiKeyStore (pla
  d5a5da4 ckpt 362: K2 research recorded: RESEARCH.md §12 (Odds API resets 1st of month, cost=mar
  84bddf3 ckpt 361: Logged Tj's 2026-09-25 ~14:00Z request (keys survive updates, usage meters, mu
  f68c0c6 ckpt 360: SHIPPED v0.6.0 (code 10): CI 36141456836 green, release 36141782030 green, 4.6
  4eebbd6 ckpt 359: pre-release: v0.6.0: manual-only scans (Scan button / pull to refresh, nothing
  5065c28 ckpt 358: Docs for v0.6.0: BRIEF (manual-only + multi-source locked decisions), CLAUDE.m
  28997ab ckpt 357: v0.6.0 code complete: full ./gradlew test 173 pass/0 fail (2 live skipped); re
  cd87838 ckpt 356: v0.6.0 app layer compiles + 15 screenshot/UI tests green: VM manual scan() wit
  14ff2f1 ckpt 355: v0.6.0 data tests green (156, 0 fail): ScannerTest rewritten for manual scan (
  471626d ckpt 354: v0.6.0 data layer compiles: PolymarketClient, KalshiClient, PinnapiClient (Ref
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
