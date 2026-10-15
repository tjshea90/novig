# CHECKPOINT 3319 — read me first, then TASKS.md

**Written:** 2026-10-15T02:56:46Z · **tests:** all 4 fast checks green
**Branch:** `ccr-4dcbaa5f-skgw6q` · **builds on:** `870e6c72b` (this checkpoint is the commit after it)

## Just done
pre-release: v0.106.0: the GitHub engine keeps games about to start young while it scans (their fair odds and likeliest Novig books are re-read during the long scan), the slow Unabated feed no longer holds a league's bets back, the engine's scan note says where the time went (timing and how far the priced lines are from +EV) (versionCode 284, v0.106.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.106.0), then run: bash tools/record-release.sh v0.106.0 284 "v0.106.0: the GitHub engine keeps games about to start young while it scans (their fair odds and likeliest Novig books are re-read during the long scan), the slow Unabated feed no longer holds a league's bets back, the engine's scan note says where the time went (timing and how far the priced lines are from +EV)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  870e6c72b ckpt 3318: pre-ship: v0.106.0: the GitHub engine keeps games about to start young whil
  26f23ed9d ckpt 3317: TCL16a: hot refresh in the scanner (fair feeds + likeliest Novig books of g
  03246fa10 ckpt 3316: v0.105.0 recorded; engine scan note carries timing; TCL16 written from live
  079c76aa5 ckpt 3315: pre-release: v0.105.0: API usage meters stay accurate with the scan on GitH
  14168bece ckpt 3314: pre-ship: v0.105.0: API usage meters stay accurate with the scan on GitHub:
  e1c031802 ckpt 3313: TCL14: usage meters merged (engine usage.json by key id, seed across handov
  da6b9cf15 ckpt 3312: TCL14 written (usage meters accurate with scans on GitHub); v0.104.0 releas
  926e29cfc ckpt 3311: pre-release: v0.104.0: Novig and BoltOdds keys can go to the GitHub engine 
  a50f88791 ckpt 3310: pre-ship: v0.104.0: Novig and BoltOdds keys can go to the GitHub engine (a 
  810737e67 ckpt 3309: TCL13: engine Novig read-only key (CloudNovigKey mint+store, CloudNovig wir
```
