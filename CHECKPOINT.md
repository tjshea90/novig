# CHECKPOINT 3323 — read me first, then TASKS.md

**Written:** 2026-10-15T03:54:57Z · **tests:** all 4 fast checks green
**Branch:** `ccr-4dcbaa5f-skgw6q` · **builds on:** `938502311` (this checkpoint is the commit after it)

## Just done
TCL16d: HotBudget (hot re-reads sized to a quarter of the route's measured speed) fixes the starvation seen on the slow public route; version bumped to 0.106.1 (285)

## Do this next
bash ship.sh for v0.106.1, trigger release.yml, record-release, then restart the engine on main (a code update; the new runner address is incidental) and read its first scan note: base prices read vs hot re-reads

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M app/build.gradle.kts
     M research/scan_speed_2026-10-15.md

## Last ten checkpoints
```
  c859d2e58 ckpt 3322: TCL17: answered 'multiple runners or APIs in parallel?' (research note + fi
  5ab2e2118 ckpt 3321: v0.106.0 released and recorded; new engine run hit RESTRICTED_NETWORK_REGIO
  34df9edd6 ckpt 3320: TCL16b: Feed freshness chip splits games within 12 h from the far ones (Clo
  d5cef8451 ckpt 3319: pre-release: v0.106.0: the GitHub engine keeps games about to start young w
  870e6c72b ckpt 3318: pre-ship: v0.106.0: the GitHub engine keeps games about to start young whil
  26f23ed9d ckpt 3317: TCL16a: hot refresh in the scanner (fair feeds + likeliest Novig books of g
  03246fa10 ckpt 3316: v0.105.0 recorded; engine scan note carries timing; TCL16 written from live
  079c76aa5 ckpt 3315: pre-release: v0.105.0: API usage meters stay accurate with the scan on GitH
  14168bece ckpt 3314: pre-ship: v0.105.0: API usage meters stay accurate with the scan on GitHub:
  e1c031802 ckpt 3313: TCL14: usage meters merged (engine usage.json by key id, seed across handov
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
