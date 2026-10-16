# CHECKPOINT 3449 — read me first, then TASKS.md

**Written:** 2026-10-16T18:19:23Z · **tests:** all 5 fast checks green
**Branch:** `ccr-95eefe49-t2qp21` · **builds on:** `c2aab4939` (this checkpoint is the commit after it)

## Just done
TCL51a: updater download no longer times out at 30 s, resumes, progress throttled, commit off main

## Do this next
ship v0.114.8, trigger release.yml, record-release, tell Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  3036ffac7 ckpt 3448: v0.114.7 released + recorded; told Tj by chat and message
  b1087861d ckpt 3447: pre-release: v0.114.7: +EV screen filters (books agreeing, all must agree; 
  a61cb4825 ckpt 3446: pre-ship: v0.114.7: +EV screen filters (books agreeing, all must agree; pho
  e52354863 ckpt 3445: TCL50 verified live: deadman.yml ran green, state.json written, replied to 
  a6c548ed5 ckpt 3444: TCL50 dead-man switch: deadman.py (+selftest), deadman.yml, coord.py/captur
  db1a6a54f ckpt 3443: TCL49: phone-only +EV books-agree filters built, data test green, app compi
  614fb31a1 ckpt 3442: forced lease, started chat watcher, replied to Tj; wrote TCL48
  d4b9aa365 ckpt 3441: session handoff: v0.114.5 released; v0.114.6 (Kalshi key send) building; Pa
  871c3d795 ckpt 3440: TCL47: ParlayAPI paid key goes first (KeyPool), engine reads plans hourly
  00103ecfa ckpt 3439: Bolt 2 keys confirmed working; v0.114.6 releasing (Kalshi send)
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
