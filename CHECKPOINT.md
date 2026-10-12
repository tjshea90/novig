# CHECKPOINT 3109 — read me first, then TASKS.md

**Written:** 2026-10-12T19:39:56Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `52ad0af9a` (this checkpoint is the commit after it)

## Just done
pre-release: v0.94.2: streaming updates no longer wait for a scan or for the bid desk (Novig lines first, refresh gap 3x its own duration, full scans only every 5 min while streaming), stale league names (LALIGA) no longer sent to Novig, timing split in Diagnostics (versionCode 235, v0.94.2)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.94.2), then run: bash tools/record-release.sh v0.94.2 235 "v0.94.2: streaming updates no longer wait for a scan or for the bid desk (Novig lines first, refresh gap 3x its own duration, full scans only every 5 min while streaming), stale league names (LALIGA) no longer sent to Novig, timing split in Diagnostics"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  52ad0af9a ckpt 3108: pre-ship: v0.94.2: streaming updates no longer wait for a scan or for the b
  95ee15844 ckpt 3107: pre-release: v0.94.1: streaming engine matches per line: a Novig push re-pr
  5ab341b8e ckpt 3106: pre-ship: v0.94.1: streaming engine matches per line: a Novig push re-price
  ba5ec75ab ckpt 3105: pre-release: v0.94.0: streaming engine (Novig and BoltOdds pushes start a s
  3c50031f2 ckpt 3104: pre-ship: v0.94.0: streaming engine (Novig and BoltOdds pushes start a scan
  8a2711b77 ckpt 3103: pre-ship: v0.94.0: streaming engine (Novig and BoltOdds pushes start a scan
  9b66c0e71 ckpt 3102: pre-release: v0.93.4: repeat and hedge guard (a side is bet or bid at most 
  21712abeb ckpt 3101: pre-ship: v0.93.4: repeat and hedge guard (a side is bet or bid at most twi
  9c78c9d5f ckpt 3100: TST2 done: RepeatGuard (pure, RepeatGuardTest) wired into ApiBetPlacer (aut
  2771ed5aa ckpt 3099: Recorded Tj's streaming-engine request as TST1-5; v0.93.3 CI/release still 
```
