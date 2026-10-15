# CHECKPOINT 3297 — read me first, then TASKS.md

**Written:** 2026-10-15T01:01:45Z · **tests:** all 4 fast checks green
**Branch:** `ccr-e8041e9d-7grwre` · **builds on:** `0a55b1e46` (this checkpoint is the commit after it)

## Just done
TCL10: cloud review fixes (bids pause, recheck, pause/kill, token removal, classification, freshness chip, settings sync, gate job, engine log), tests, docs; 0.102.0

## Do this next
full tests with -Pscreenshots, look at PNGs, ship.sh v0.102.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M CLAUDE.md
     M TASKS.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  d022bf6e3 ckpt 3296: TCL10 written (review/fix/simplify/mirror/refresh/freshness)
  e4cb95841 ckpt 3295: v0.101.0 released and recorded; engine restarted on the new engine code
  669e24f8f ckpt 3294: pre-release: v0.101.0: pull to refresh and Scan ask the GitHub engine to sc
  63c786cfa ckpt 3293: TCL9: refresh requests, progress, measured currency stat, settings mirror (
  5a182e3ae ckpt 3292: v0.100.2 recorded; engine now has 13 feeds from the app-sent keys and publi
  306606dff ckpt 3291: engine: settings change late in a scan waits for next pass (switch-off stil
  42de5fb66 ckpt 3290: pre-release: v0.100.2: cloud engine sends heartbeats during its first long 
  9df5958eb ckpt 3289: TCL8: heartbeat during long scans, sendKeys no longer restarts the engine f
  f7d2fc39f ckpt 3288: v0.100.1 released and recorded (TCL7)
  b28e3dd5a ckpt 3287: pre-release: v0.100.1: the app sends your API keys to GitHub secrets for th
```

(26 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
