# CHECKPOINT 362 — read me first, then TASKS.md

**Written:** 2026-09-25T14:39:07Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `84bddf3` (this checkpoint is the commit after it)

## Just done
K2 research recorded: RESEARCH.md §12 (Odds API resets 1st of month, cost=markets x regions, 0 if empty, headers, OUT_OF_USAGE_CREDITS, 30/s; pinnapi 100/day 100/h 20/min, UTC midnight reset, 429 window+retry_after_ms, no usage headers, ToS forbids circumventing rate limits; Polymarket/Kalshi/Novig limits) + metering design

## Do this next
K1: FileApiKeyStore (plain JSON, migration from EncryptedApiKeyStore, export/import); K3: Quota policies + UsageMeter ledger (usage.json) + KeyPool rotation replacing KeyRotator in TheOddsApiClient/PinnapiClient (multi pinnapi keys); keyless counters

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M RESEARCH.md

## Last ten checkpoints
```
  84bddf3 ckpt 361: Logged Tj's 2026-09-25 ~14:00Z request (keys survive updates, usage meters, mu
  f68c0c6 ckpt 360: SHIPPED v0.6.0 (code 10): CI 36141456836 green, release 36141782030 green, 4.6
  4eebbd6 ckpt 359: pre-release: v0.6.0: manual-only scans (Scan button / pull to refresh, nothing
  5065c28 ckpt 358: Docs for v0.6.0: BRIEF (manual-only + multi-source locked decisions), CLAUDE.m
  28997ab ckpt 357: v0.6.0 code complete: full ./gradlew test 173 pass/0 fail (2 live skipped); re
  cd87838 ckpt 356: v0.6.0 app layer compiles + 15 screenshot/UI tests green: VM manual scan() wit
  14ff2f1 ckpt 355: v0.6.0 data tests green (156, 0 fail): ScannerTest rewritten for manual scan (
  471626d ckpt 354: v0.6.0 data layer compiles: PolymarketClient, KalshiClient, PinnapiClient (Ref
  2038b93 ckpt 353: v0.6.0 in progress (build intentionally broken mid-refactor): ReferenceModels 
  760fce5 ckpt 352: Research R3/R4/R5 saved: RESEARCH.md §11 (measured Novig public edge limit ~4
```
