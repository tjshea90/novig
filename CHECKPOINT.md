# CHECKPOINT 4551 — read me first, then TASKS.md

**Written:** 2026-11-16T20:46:20Z · **tests:** all 10 fast checks green
**Branch:** `claude/optimistic-curie-cnhej8` · **builds on:** `5328bf0e8` (this checkpoint is the commit after it)

## Just done
pre-release: Engine tab redraws every 5 s instead of every second (12 s on low-RAM tablets): far less CPU (versionCode 466, v1.0.126)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.126), then run: bash tools/record-release.sh v1.0.126 466 "Engine tab redraws every 5 s instead of every second (12 s on low-RAM tablets): far less CPU"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  5328bf0e8 ckpt 4550: pre-ship: Engine tab redraws every 5 s instead of every second (12 s on low
  4ba4b9f15 ckpt 4549: pre-release: Baseline profile: precompiles the app's code (tablet cold star
  bb8e3a59a ckpt 4548: pre-ship: Baseline profile: precompiles the app's code (tablet cold start 1
  8d0bbb0f5 ckpt 4547: PF6a baseline profile for tablet cold start
  eb4eeb371 ckpt 4546: answered Tj slow start/crash; PF6 logged from 20:15 diag
  e94feb789 ckpt 4545: answered Tj; PF5 logged
  0665ff342 ckpt 4544: answered Tj lag; shipped v1.0.124; PF4 logged
  e836e73e1 ckpt 4543: WATCH 2026-11-16: engine healthy, no change
  48cac1414 ckpt 4542: pre-release: Diagnostics names which tab stutters and flags a slow screen w
  9ec516842 ckpt 4541: pre-ship: Diagnostics names which tab stutters and flags a slow screen with
```
