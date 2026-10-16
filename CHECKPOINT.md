# CHECKPOINT 3451 — read me first, then TASKS.md

**Written:** 2026-10-16T18:22:11Z · **tests:** all 5 fast checks green
**Branch:** `ccr-95eefe49-t2qp21` · **builds on:** `89cb5f494` (this checkpoint is the commit after it)

## Just done
pre-release: v0.114.8: app update no longer times out or freezes (download resumes, progress throttled, install off the main thread) (versionCode 304, v0.114.8)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.114.8), then run: bash tools/record-release.sh v0.114.8 304 "v0.114.8: app update no longer times out or freezes (download resumes, progress throttled, install off the main thread)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  89cb5f494 ckpt 3450: TCL52 recorded (no build): All agree chip cut off, chat blank with keyboard
  cc56a766c ckpt 3449: TCL51a: updater download no longer times out at 30 s, resumes, progress thr
  3036ffac7 ckpt 3448: v0.114.7 released + recorded; told Tj by chat and message
  b1087861d ckpt 3447: pre-release: v0.114.7: +EV screen filters (books agreeing, all must agree; 
  a61cb4825 ckpt 3446: pre-ship: v0.114.7: +EV screen filters (books agreeing, all must agree; pho
  e52354863 ckpt 3445: TCL50 verified live: deadman.yml ran green, state.json written, replied to 
  a6c548ed5 ckpt 3444: TCL50 dead-man switch: deadman.py (+selftest), deadman.yml, coord.py/captur
  db1a6a54f ckpt 3443: TCL49: phone-only +EV books-agree filters built, data test green, app compi
  614fb31a1 ckpt 3442: forced lease, started chat watcher, replied to Tj; wrote TCL48
  d4b9aa365 ckpt 3441: session handoff: v0.114.5 released; v0.114.6 (Kalshi key send) building; Pa
```
