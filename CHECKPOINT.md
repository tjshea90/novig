# CHECKPOINT 3050 — read me first, then TASKS.md

**Written:** 2026-10-12T05:56:24Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `a147eca86` (this checkpoint is the commit after it)

## Just done
TBO3 (data side, part 1): data/.../boltodds/ Models, Parser, Markets, Books, Board, Convert, JsonArray, Client (REST+streamed snapshot), Stream (WS lane), Feed, Sources; ApiProvider.BOLTODDS, QuotaPolicy.BOLTODDS, ApiGates.useBoltOdds/boltAllBooks, enabledSources, SOURCE_ORDER (after oddspapi_free), ApiProbe branch; BoltOddsTest 23 green

## Do this next
Next: mock-server tests for client/stream/feed (BoltOddsClientTest, BoltOddsStreamTest); then app wiring: ApiEntry.BOLTODDS, VigilantApp (client/feed/sources, boltActive, flatRateSources), MainViewModel (keys state, Test key + sample), Settings page + SettingsIndex + pages test, Diagnostics report, UsageMeters, ApiTogglesTest idsOf, LiveOnlyTest, HealthChecks; BoltKeyTest sample; BOLTODDS_API.md §7; RESEARCH.md verdict; then full floor + ship v0.90.0.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  9bd2933e8 ckpt 3049: TBO2 done: Novig docs re-read (135 pages + OpenAPI), findings in research/n
  678d21dbb ckpt 3046: TBO1: BoltOdds docs read in full (single page), saved raw to research/bolto
  a3856d33a ckpt 3045: TBO: wrote Tj's BoltOdds Pro + Novig docs request into TASKS.md (TBO1-TBO5)
  cc60dda84 ckpt 3044: TAN7 done: v0.89.35 released (Release run 38204510821, recorded in BUILDLOG
  5e530badd ckpt 3043: pre-release: v0.89.35: Over-prop edge bar, lead-time Kelly scale, bid desk 
  e299c8137 ckpt 3042: TAN: NET event line carries the start of a 400's body; follow-ups (e)(f) wr
  8a91641a5 ckpt 3041: version 0.89.35; TASKS TAN2-TAN6 ticked with evidence; skill doc lists the 
  b1b4d24d0 ckpt 3040: RESEARCH §135 written (the TAN analysis + what was acted on and what was n
  f8f04ecbd ckpt 3039: TAN6: Kelly stake scaled by lead time (EvMath.leadScale: 1.0 up to 9h, 0.6 
  93fe0376d ckpt 3038: TAN4: Settings home 20 -> 14 rows: APIs & keys hub with child pages (SGO, O
```

(22 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
