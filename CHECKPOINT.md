# CHECKPOINT 3291 — read me first, then TASKS.md

**Written:** 2026-10-14T23:28:58Z · **tests:** all 4 fast checks green
**Branch:** `ccr-e8041e9d-7grwre` · **builds on:** `01bba3871` (this checkpoint is the commit after it)

## Just done
engine: settings change late in a scan waits for next pass (switch-off still immediate); release.yml triggered for v0.100.2

## Do this next
confirm v0.100.2 release, record-release; restart engine once to pick up engine change; read first full scan time

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  42de5fb66 ckpt 3290: pre-release: v0.100.2: cloud engine sends heartbeats during its first long 
  9df5958eb ckpt 3289: TCL8: heartbeat during long scans, sendKeys no longer restarts the engine f
  f7d2fc39f ckpt 3288: v0.100.1 released and recorded (TCL7)
  b28e3dd5a ckpt 3287: pre-release: v0.100.1: the app sends your API keys to GitHub secrets for th
  948771d04 ckpt 3286: TCL7: app sends API keys to GitHub secrets (SealedBox, sendKeys, button), d
  05f8377e1 ckpt 3285: v0.100.0 released and recorded (TCL5 done)
  9f974b523 ckpt 3284: research doc: built + first live run; Diagnostics CLOUD section; release.ym
  aa9eb8fc2 ckpt 3283: pre-release: v0.100.0: Cloud scan - the scans run on GitHub (engine, cloud-
  76db0ed58 ckpt 3282: pre-release: v0.100.0: Cloud scan - the scans run on GitHub (engine, cloud-
  6cc83b134 ckpt 3281: TCL: cloudScan/cloudSave moved into gates (ScanSettings slot limit), off-ow
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
