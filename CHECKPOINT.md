# CHECKPOINT 363 — read me first, then TASKS.md

**Written:** 2026-09-25T14:43:18Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `bb18349` (this checkpoint is the commit after it)

## Just done
K1/K3 data layer (build compiles, tests not yet updated): FileApiKeyStore (plain JSON, export/import merge); Usage.kt = QuotaPolicy per provider (Odds API monthly UTC 500, pinnapi daily 100 + 20/min, keyless Novig/Polymarket/Kalshi), persistent UsageMeter ledger (server headers trusted, local counts otherwise, pre-emptive skip, depleted until reset, re-probe 6h after a reset that didn't happen, billing-cycle detection), KeyPool rotation from key 1; KeyRotator removed; TheOddsApiClient + PinnapiClient (multi-key) on KeyPool; keyless request counting in Novig/Polymarket/Kalshi clients

## Do this next
Update TheOddsApiClientTest + ExchangeClientsTest pinnapi tests to KeyPool; new UsageMeterTest (periods, pick order, reset to key 1, pre-emptive skip, billing detection, persistence) + FileApiKeyStoreTest (round trip, import merge); then app: migration from EncryptedApiKeyStore, AppContainer wiring, meters UI, multi pinnapi keys UI, export/import

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  d5a5da4 ckpt 362: K2 research recorded: RESEARCH.md §12 (Odds API resets 1st of month, cost=mar
  84bddf3 ckpt 361: Logged Tj's 2026-09-25 ~14:00Z request (keys survive updates, usage meters, mu
  f68c0c6 ckpt 360: SHIPPED v0.6.0 (code 10): CI 36141456836 green, release 36141782030 green, 4.6
  4eebbd6 ckpt 359: pre-release: v0.6.0: manual-only scans (Scan button / pull to refresh, nothing
  5065c28 ckpt 358: Docs for v0.6.0: BRIEF (manual-only + multi-source locked decisions), CLAUDE.m
  28997ab ckpt 357: v0.6.0 code complete: full ./gradlew test 173 pass/0 fail (2 live skipped); re
  cd87838 ckpt 356: v0.6.0 app layer compiles + 15 screenshot/UI tests green: VM manual scan() wit
  14ff2f1 ckpt 355: v0.6.0 data tests green (156, 0 fail): ScannerTest rewritten for manual scan (
  471626d ckpt 354: v0.6.0 data layer compiles: PolymarketClient, KalshiClient, PinnapiClient (Ref
  2038b93 ckpt 353: v0.6.0 in progress (build intentionally broken mid-refactor): ReferenceModels 
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
