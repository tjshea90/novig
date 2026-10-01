# CHECKPOINT 2240 — read me first, then TASKS.md

**Written:** 2026-10-01T04:11:24Z · **tests:** all 3 fast checks green
**Branch:** `ccr-650fc40f-rtt4er` · **builds on:** `31ff8423` (this checkpoint is the commit after it)

## Just done
pre-release: v0.38.0: background auto-scan every 15 sec, 30 sec, 1 min or 3 min (beside 5-40 min; the schedule re-arms when a cycle outlasts its interval, Vigilant's own scan at most every 4 min); CLV from real closes only (a bet is never closed at its own price, a second read ~2 min before the start, an older read never replaces a fresher close) (versionCode 68, v0.38.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.38.0), then run: bash tools/record-release.sh v0.38.0 68 "v0.38.0: background auto-scan every 15 sec, 30 sec, 1 min or 3 min (beside 5-40 min; the schedule re-arms when a cycle outlasts its interval, Vigilant's own scan at most every 4 min); CLV from real closes only (a bet is never closed at its own price, a second read ~2 min before the start, an older read never replaces a fresher close)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  31ff8423 ckpt 2239: AA1-AA4 done: interval in seconds, fast-cycle safety, CLV real closes; floor
  c08d3611 ckpt 2238: AA2/AA3: at-bet no longer a close, final read ~110 s before start (needsFina
  b86607f3 ckpt 2237: AA1+AA2 core: autoScanSeconds (15/30/60/180 s + 5-40 min, schema 12 migratio
  5a61e310 ckpt 2236: AA: wrote Tj's auto-scan interval + CLV request into TASKS.md as AA1-AA5 wit
  bfd74452 ckpt 2235: v0.37.0 (code 67) released and recorded: Z1-Z5 done (tennis through ParlayAP
  c94e2078 ckpt 2234: pre-release: v0.37.0: tennis through ParlayAPI (bet365, Caesars, DraftKings,
  11118acf ckpt 2233: pre-ship: v0.37.0: tennis through ParlayAPI (bet365, Caesars, DraftKings, Be
  da21ef82 ckpt 2232: light test (Z4): who-else check clean, screenshot 5h_settings_fair_parlay_on
  11541887 ckpt 2231: Z3: ParlayBooks tennis (units + 1-day gap), ParlayCloses tennis closes in th
  2eec4225 ckpt 2230: Z2 done + live-verified: 63/64 Novig tennis matches paired via ParlayAPI, SE
```
