# CHECKPOINT 3113 — read me first, then TASKS.md

**Written:** 2026-10-12T20:22:25Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `b35e1fd55` (this checkpoint is the commit after it)

## Just done
pre-release: v0.95.1: BoltOdds live feed reads live tennis (match winner), explains why it has no live game, BoltOdds board conversion cached per entry (refresh was 7-18 s), streaming update timing breakdown (versionCode 237, v0.95.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.95.1), then run: bash tools/record-release.sh v0.95.1 237 "v0.95.1: BoltOdds live feed reads live tennis (match winner), explains why it has no live game, BoltOdds board conversion cached per entry (refresh was 7-18 s), streaming update timing breakdown"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  b35e1fd55 ckpt 3112: pre-ship: v0.95.1: BoltOdds live feed reads live tennis (match winner), exp
  a8570156a ckpt 3111: pre-release: v0.95.0: live engine on BoltOdds' soft-book average (BoltOdds 
  ac3fabfd4 ckpt 3110: Recorded TBL1-5 (BoltOdds soft-book live engine). v0.94.2 release pending (
  49c395073 ckpt 3109: pre-release: v0.94.2: streaming updates no longer wait for a scan or for th
  52ad0af9a ckpt 3108: pre-ship: v0.94.2: streaming updates no longer wait for a scan or for the b
  95ee15844 ckpt 3107: pre-release: v0.94.1: streaming engine matches per line: a Novig push re-pr
  5ab341b8e ckpt 3106: pre-ship: v0.94.1: streaming engine matches per line: a Novig push re-price
  ba5ec75ab ckpt 3105: pre-release: v0.94.0: streaming engine (Novig and BoltOdds pushes start a s
  3c50031f2 ckpt 3104: pre-ship: v0.94.0: streaming engine (Novig and BoltOdds pushes start a scan
  8a2711b77 ckpt 3103: pre-ship: v0.94.0: streaming engine (Novig and BoltOdds pushes start a scan
```
