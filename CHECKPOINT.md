# CHECKPOINT 3293 — read me first, then TASKS.md

**Written:** 2026-10-15T00:05:01Z · **tests:** all 4 fast checks green
**Branch:** `ccr-e8041e9d-7grwre` · **builds on:** `23c14fa4c` (this checkpoint is the commit after it)

## Just done
TCL9: refresh requests, progress, measured currency stat, settings mirror (CloudScanKey); 0.101.0

## Do this next
ship.sh v0.101.0, release, restart engine for new engine code, read first refresh round trip

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M CLAUDE.md
     M TASKS.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  5a182e3ae ckpt 3292: v0.100.2 recorded; engine now has 13 feeds from the app-sent keys and publi
  306606dff ckpt 3291: engine: settings change late in a scan waits for next pass (switch-off stil
  42de5fb66 ckpt 3290: pre-release: v0.100.2: cloud engine sends heartbeats during its first long 
  9df5958eb ckpt 3289: TCL8: heartbeat during long scans, sendKeys no longer restarts the engine f
  f7d2fc39f ckpt 3288: v0.100.1 released and recorded (TCL7)
  b28e3dd5a ckpt 3287: pre-release: v0.100.1: the app sends your API keys to GitHub secrets for th
  948771d04 ckpt 3286: TCL7: app sends API keys to GitHub secrets (SealedBox, sendKeys, button), d
  05f8377e1 ckpt 3285: v0.100.0 released and recorded (TCL5 done)
  9f974b523 ckpt 3284: research doc: built + first live run; Diagnostics CLOUD section; release.ym
  aa9eb8fc2 ckpt 3283: pre-release: v0.100.0: Cloud scan - the scans run on GitHub (engine, cloud-
```

(11 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
