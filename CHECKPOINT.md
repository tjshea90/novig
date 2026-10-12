# CHECKPOINT 3119 — read me first, then TASKS.md

**Written:** 2026-10-12T21:07:18Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `6dad339a3` (this checkpoint is the commit after it)

## Just done
pre-release: v0.95.3: BoltOdds live note lists per-game fresh companies; re-subscribe honours the server's wait-minutes refusal (versionCode 239, v0.95.3)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.95.3), then run: bash tools/record-release.sh v0.95.3 239 "v0.95.3: BoltOdds live note lists per-game fresh companies; re-subscribe honours the server's wait-minutes refusal"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6dad339a3 ckpt 3118: pre-ship: v0.95.3: BoltOdds live note lists per-game fresh companies; re-su
  007ef81b9 ckpt 3117: TBL7a: BoltOdds re-subscribe honours 'wait N minutes' (test), live note has
  65135985f ckpt 3116: Read v0.95.2 diagnostics; BoltOdds live note now lists per-game fresh compa
  d6bd50d78 ckpt 3115: pre-release: v0.95.2: BoltOdds live feed prints why it has no game (Diagnos
  7ecab9285 ckpt 3114: pre-ship: v0.95.2: BoltOdds live feed prints why it has no game (Diagnostic
  d75f4c4f1 ckpt 3113: pre-release: v0.95.1: BoltOdds live feed reads live tennis (match winner), 
  b35e1fd55 ckpt 3112: pre-ship: v0.95.1: BoltOdds live feed reads live tennis (match winner), exp
  a8570156a ckpt 3111: pre-release: v0.95.0: live engine on BoltOdds' soft-book average (BoltOdds 
  ac3fabfd4 ckpt 3110: Recorded TBL1-5 (BoltOdds soft-book live engine). v0.94.2 release pending (
  49c395073 ckpt 3109: pre-release: v0.94.2: streaming updates no longer wait for a scan or for th
```
