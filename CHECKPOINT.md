# CHECKPOINT 3289 — read me first, then TASKS.md

**Written:** 2026-10-14T23:24:46Z · **tests:** all 4 fast checks green
**Branch:** `ccr-e8041e9d-7grwre` · **builds on:** `4afc40cec` (this checkpoint is the commit after it)

## Just done
TCL8: heartbeat during long scans, sendKeys no longer restarts the engine for unchanged keys, first-scan message; 0.100.2

## Do this next
ship.sh v0.100.2, release, then read cloud-feed status for the first full scan time

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  f7d2fc39f ckpt 3288: v0.100.1 released and recorded (TCL7)
  b28e3dd5a ckpt 3287: pre-release: v0.100.1: the app sends your API keys to GitHub secrets for th
  948771d04 ckpt 3286: TCL7: app sends API keys to GitHub secrets (SealedBox, sendKeys, button), d
  05f8377e1 ckpt 3285: v0.100.0 released and recorded (TCL5 done)
  9f974b523 ckpt 3284: research doc: built + first live run; Diagnostics CLOUD section; release.ym
  aa9eb8fc2 ckpt 3283: pre-release: v0.100.0: Cloud scan - the scans run on GitHub (engine, cloud-
  76db0ed58 ckpt 3282: pre-release: v0.100.0: Cloud scan - the scans run on GitHub (engine, cloud-
  6cc83b134 ckpt 3281: TCL: cloudScan/cloudSave moved into gates (ScanSettings slot limit), off-ow
  47ec326c1 ckpt 3280: TCL3/TCL4: phone CloudLink (poll, settings push, GitHub history saving), Cl
  650b7053e ckpt 3279: TCL2: CloudEngine (+tests), :cloud module (CloudMain publisher), cloud-scan
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
