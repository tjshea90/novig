# CHECKPOINT 3117 — read me first, then TASKS.md

**Written:** 2026-10-12T21:03:58Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `3e1c117e9` (this checkpoint is the commit after it)

## Just done
TBL7a: BoltOdds re-subscribe honours 'wait N minutes' (test), live note has per-game freshness

## Do this next
Ship v0.95.3 (bash ship.sh), release, ask Tj for a diagnostics file during a live slate; then decide quorum/age or is_live semantics from the note

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M data/src/test/kotlin/com/tjshea/vigilant/data/boltodds/BoltOddsLiveTest.kt

## Last ten checkpoints
```
  65135985f ckpt 3116: Read v0.95.2 diagnostics; BoltOdds live note now lists per-game fresh compa
  d6bd50d78 ckpt 3115: pre-release: v0.95.2: BoltOdds live feed prints why it has no game (Diagnos
  7ecab9285 ckpt 3114: pre-ship: v0.95.2: BoltOdds live feed prints why it has no game (Diagnostic
  d75f4c4f1 ckpt 3113: pre-release: v0.95.1: BoltOdds live feed reads live tennis (match winner), 
  b35e1fd55 ckpt 3112: pre-ship: v0.95.1: BoltOdds live feed reads live tennis (match winner), exp
  a8570156a ckpt 3111: pre-release: v0.95.0: live engine on BoltOdds' soft-book average (BoltOdds 
  ac3fabfd4 ckpt 3110: Recorded TBL1-5 (BoltOdds soft-book live engine). v0.94.2 release pending (
  49c395073 ckpt 3109: pre-release: v0.94.2: streaming updates no longer wait for a scan or for th
  52ad0af9a ckpt 3108: pre-ship: v0.94.2: streaming updates no longer wait for a scan or for the b
  95ee15844 ckpt 3107: pre-release: v0.94.1: streaming engine matches per line: a Novig push re-pr
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
