# CHECKPOINT 3325 — read me first, then TASKS.md

**Written:** 2026-10-15T03:59:18Z · **tests:** all 4 fast checks green
**Branch:** `ccr-4dcbaa5f-skgw6q` · **builds on:** `dbdb32f50` (this checkpoint is the commit after it)

## Just done
v0.106.1 shipped (3690 tests green), release.yml triggered, engine run 38673681637 cancelled and cloud-scan.yml dispatched on main so the engine runs HotBudget (a code update; the new runner address is incidental)

## Do this next
Confirm v0.106.1 release (get_release_by_tag) then bash tools/record-release.sh v0.106.1 285 with the ship note; read st.sh after ~15 min for the new engine: base Novig prices read vs hot re-reads per pass, novigRoute; tell Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  dbdb32f50 ckpt 3324: pre-release: v0.106.1: the GitHub engine's hot refresh no longer starves th
  753e1222d ckpt 3323: TCL16d: HotBudget (hot re-reads sized to a quarter of the route's measured 
  c859d2e58 ckpt 3322: TCL17: answered 'multiple runners or APIs in parallel?' (research note + fi
  5ab2e2118 ckpt 3321: v0.106.0 released and recorded; new engine run hit RESTRICTED_NETWORK_REGIO
  34df9edd6 ckpt 3320: TCL16b: Feed freshness chip splits games within 12 h from the far ones (Clo
  d5cef8451 ckpt 3319: pre-release: v0.106.0: the GitHub engine keeps games about to start young w
  870e6c72b ckpt 3318: pre-ship: v0.106.0: the GitHub engine keeps games about to start young whil
  26f23ed9d ckpt 3317: TCL16a: hot refresh in the scanner (fair feeds + likeliest Novig books of g
  03246fa10 ckpt 3316: v0.105.0 recorded; engine scan note carries timing; TCL16 written from live
  079c76aa5 ckpt 3315: pre-release: v0.105.0: API usage meters stay accurate with the scan on GitH
```
