# CHECKPOINT 4328 — read me first, then TASKS.md

**Written:** 2026-10-31T01:03:28Z · **tests:** all 9 fast checks green
**Branch:** `ccr-230827ca-lcskn8` · **builds on:** `89235075c` (this checkpoint is the commit after it)

## Just done
pre-release: ST1: the Engine banner says how old a trading stop is and when it retries (versionCode 436, v1.0.96)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.96), then run: bash tools/record-release.sh v1.0.96 436 "ST1: the Engine banner says how old a trading stop is and when it retries"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  89235075c ckpt 4327: pre-ship: ST1: the Engine banner says how old a trading stop is and when it
  243c09627 ckpt 4326: ENGINEER 2026-10-30: engine ordering blocked by Novig location check + refu
  918fa7d34 ckpt 4325: pre-release: TK1: both devices can share one Novig trading key (save on one
  214aa0f42 ckpt 4324: pre-ship: TK1: both devices can share one Novig trading key (save on one, u
  851a476f4 ckpt 4323: pre-release: KX1c: the Bring my API keys back result shows right under its 
  6b2227c21 ckpt 4322: pre-ship: KX1c: the Bring my API keys back result shows right under its but
  f2df0ddae ckpt 4321: ANALYST 2026-10-30 no change
  0c343688c ckpt 4320: WATCH 2026-10-30: engine healthy, no change
  c44bf658f ckpt 4319: pre-release: FB1: full encrypted backup and restore of the whole app, keys 
  6553a4de8 ckpt 4318: pre-ship: FB1: full encrypted backup and restore of the whole app, keys inc
```
