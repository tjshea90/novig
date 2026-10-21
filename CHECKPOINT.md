# CHECKPOINT 3931 — read me first, then TASKS.md

**Written:** 2026-10-21T23:08:48Z · **tests:** all 9 fast checks green
**Branch:** `claude/new-session-51eqix` · **builds on:** `b07c94395` (this checkpoint is the commit after it)

## Just done
SA2/SA3 code: SharpApiClient (900/min, 4 in flight, 401/429 handling, usage meter), SharpApiFeed (league load by cursor + per-league /odds/delta, hot 2 s / cold 15 s), SharpApiConvert + SharpApiBooks + SharpApiSource; wired: ApiProvider, QuotaPolicy, ApiProbe Test (/account + /account/usage), CloudKeys SHARPAPI_API_KEY + cloud-scan.yml, CloudSources, ApiToggles, CloudScanKey, ApiMonitor, ApiManager, SOURCE_ORDER/HOT_FEEDS, phone ApiEntry + Settings page + index + phone source; Circa/Bookmaker sharp (schema 23). Compiles.

## Do this next
tests: SharpApiConvertTest, SharpApiFeedTest (MockWebServer), fix ApiTogglesTest/AltMarketsTest schema; then SA4 live feed

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M cloud/src/main/kotlin/com/tjshea/vigilant/cloud/CloudMain.kt
     M data/src/main/kotlin/com/tjshea/vigilant/data/cloud/CloudSources.kt

## Last ten checkpoints
```
  41c3fa5ea ckpt 3930: SA1: SharpAPI docs saved (39 pages + llms-full) and SHARPAPI.md written
  2ccee2a88 ckpt 3929: v1.0.41 recorded; engine verified: CNO answers the runner (100 rows, good r
  f88f264b1 ckpt 3928: TR: four trial APIs judged (research/api_trials_2026-10-21.md): SharpAPI wo
  a7b61ccd6 ckpt 3927: pre-release: v1.0.41: full test fixes: Kalshi CNO rows open at Kalshi every
  8047b101b ckpt 3926: full test (CK5/XO6) done: floor 4135 green, 134 screens viewed, 7 fixes (se
  77c72f06e ckpt 3925: v1.0.40 released (CK). Full test in progress: Kalshi CNO rows' alerts/taps 
  d283653f0 ckpt 3924: pre-release: v1.0.40: CNO scans Novig and Kalshi (tab chips, Kalshi EV net 
  6916f0eaf ckpt 3923: CK2/CK3 built: CnoVenueSource (Novig+Kalshi reads, Kalshi EV net of fee), v
  038eed8e0 ckpt 3922: v1.0.39 released (BC3,BC4)
  e8d6f4aca ckpt 3921: pre-release: v1.0.39: engine closes BoltOdds/Kalshi/SX sockets cleanly on j
```

(10 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
