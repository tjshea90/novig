# CHECKPOINT 3324 — read me first, then TASKS.md

**Written:** 2026-10-15T03:58:58Z · **tests:** all 4 fast checks green
**Branch:** `ccr-4dcbaa5f-skgw6q` · **builds on:** `753e1222d` (this checkpoint is the commit after it)

## Just done
pre-release: v0.106.1: the GitHub engine's hot refresh no longer starves the scan on a slow route (its Novig re-reads are sized to a quarter of the route's measured speed), and the Feed's freshness chip tells the games within 12 hours apart from the far ones (versionCode 285, v0.106.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.106.1), then run: bash tools/record-release.sh v0.106.1 285 "v0.106.1: the GitHub engine's hot refresh no longer starves the scan on a slow route (its Novig re-reads are sized to a quarter of the route's measured speed), and the Feed's freshness chip tells the games within 12 hours apart from the far ones"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  753e1222d ckpt 3323: TCL16d: HotBudget (hot re-reads sized to a quarter of the route's measured 
  c859d2e58 ckpt 3322: TCL17: answered 'multiple runners or APIs in parallel?' (research note + fi
  5ab2e2118 ckpt 3321: v0.106.0 released and recorded; new engine run hit RESTRICTED_NETWORK_REGIO
  34df9edd6 ckpt 3320: TCL16b: Feed freshness chip splits games within 12 h from the far ones (Clo
  d5cef8451 ckpt 3319: pre-release: v0.106.0: the GitHub engine keeps games about to start young w
  870e6c72b ckpt 3318: pre-ship: v0.106.0: the GitHub engine keeps games about to start young whil
  26f23ed9d ckpt 3317: TCL16a: hot refresh in the scanner (fair feeds + likeliest Novig books of g
  03246fa10 ckpt 3316: v0.105.0 recorded; engine scan note carries timing; TCL16 written from live
  079c76aa5 ckpt 3315: pre-release: v0.105.0: API usage meters stay accurate with the scan on GitH
  14168bece ckpt 3314: pre-ship: v0.105.0: API usage meters stay accurate with the scan on GitHub:
```
