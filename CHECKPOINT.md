# CHECKPOINT 4553 — read me first, then TASKS.md

**Written:** 2026-11-16T20:55:23Z · **tests:** all 10 fast checks green
**Branch:** `claude/optimistic-curie-cnhej8` · **builds on:** `6f4576aed` (this checkpoint is the commit after it)

## Just done
pre-release: Live tab timers slow down on low-RAM tablets (versionCode 467, v1.0.127)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.127), then run: bash tools/record-release.sh v1.0.127 467 "Live tab timers slow down on low-RAM tablets"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6f4576aed ckpt 4552: pre-ship: Live tab timers slow down on low-RAM tablets
  710fa073a ckpt 4551: pre-release: Engine tab redraws every 5 s instead of every second (12 s on 
  5328bf0e8 ckpt 4550: pre-ship: Engine tab redraws every 5 s instead of every second (12 s on low
  4ba4b9f15 ckpt 4549: pre-release: Baseline profile: precompiles the app's code (tablet cold star
  bb8e3a59a ckpt 4548: pre-ship: Baseline profile: precompiles the app's code (tablet cold start 1
  8d0bbb0f5 ckpt 4547: PF6a baseline profile for tablet cold start
  eb4eeb371 ckpt 4546: answered Tj slow start/crash; PF6 logged from 20:15 diag
  e94feb789 ckpt 4545: answered Tj; PF5 logged
  0665ff342 ckpt 4544: answered Tj lag; shipped v1.0.124; PF4 logged
  e836e73e1 ckpt 4543: WATCH 2026-11-16: engine healthy, no change
```
