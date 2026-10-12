# CHECKPOINT 3124 — read me first, then TASKS.md

**Written:** 2026-10-12T21:47:16Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `4c92a5731` (this checkpoint is the commit after it)

## Just done
pre-release: v0.95.4: BoltOdds board conversion no longer redone for every book push (refresh passes took 8-15 s) (versionCode 240, v0.95.4)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.95.4), then run: bash tools/record-release.sh v0.95.4 240 "v0.95.4: BoltOdds board conversion no longer redone for every book push (refresh passes took 8-15 s)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  4c92a5731 ckpt 3123: TBL8: BoltOdds conversion cache fixed (aliveAt no longer invalidates), test
  1e793c057 ckpt 3122: PropLine vs ParlayAPI pregame comparison written
  4354d30e0 ckpt 3121: Read v0.95.3 diagnostics; evidence note
  1d5039705 ckpt 3120: PropLine Streaming Lite investigated (vendor docs), note written
  eb2e43f16 ckpt 3119: pre-release: v0.95.3: BoltOdds live note lists per-game fresh companies; re
  6dad339a3 ckpt 3118: pre-ship: v0.95.3: BoltOdds live note lists per-game fresh companies; re-su
  007ef81b9 ckpt 3117: TBL7a: BoltOdds re-subscribe honours 'wait N minutes' (test), live note has
  65135985f ckpt 3116: Read v0.95.2 diagnostics; BoltOdds live note now lists per-game fresh compa
  d6bd50d78 ckpt 3115: pre-release: v0.95.2: BoltOdds live feed prints why it has no game (Diagnos
  7ecab9285 ckpt 3114: pre-ship: v0.95.2: BoltOdds live feed prints why it has no game (Diagnostic
```
