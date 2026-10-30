# CHECKPOINT 4319 — read me first, then TASKS.md

**Written:** 2026-10-30T03:47:45Z · **tests:** all 9 fast checks green
**Branch:** `ccr-230827ca-lcskn8` · **builds on:** `6553a4de8` (this checkpoint is the commit after it)

## Just done
pre-release: FB1: full encrypted backup and restore of the whole app, keys included, for Samsung Secure Folder (versionCode 433, v1.0.93)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.93), then run: bash tools/record-release.sh v1.0.93 433 "FB1: full encrypted backup and restore of the whole app, keys included, for Samsung Secure Folder"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6553a4de8 ckpt 4318: pre-ship: FB1: full encrypted backup and restore of the whole app, keys inc
  0d173b549 ckpt 4317: FB1: full encrypted backup/restore of the whole app incl. keys (FullBackup,
  0bef21369 ckpt 4315: ANALYST 2026-10-29: no rule change, note + card posted
  23ba9fa98 ckpt 4314: CHAT 2026-10-29: turned on propLine/parlay/sx/novig sockets (tablet default
  c875a5bed ckpt 4313: WATCH 2026-10-29 engine healthy, no change
  0ab8edc90 ckpt 4312: ENGINEER 2026-10-29 watch only, engine healthy, no change
  5a0f22d1a ckpt 4311: pre-release: HR2: imported fair-value settlements keep their profit (all-ti
  586319a54 ckpt 4310: KX1/HR1 done: encrypted key export (keys-export.yml), history restore + fil
  fbecf50b9 ckpt 4309: pre-release: KX1b: bring API keys back from GitHub's encrypted export onto 
  48e940f48 ckpt 4308: pre-release: HR1: restore settled bets and bids from the GitHub backup or a
```
