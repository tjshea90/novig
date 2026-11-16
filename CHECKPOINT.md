# CHECKPOINT 4556 — read me first, then TASKS.md

**Written:** 2026-11-16T21:12:16Z · **tests:** all 10 fast checks green
**Branch:** `claude/optimistic-curie-cnhej8` · **builds on:** `926262425` (this checkpoint is the commit after it)

## Just done
pre-ship: Launch trace: the next report says how far a run got before Android ended it

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M app/build.gradle.kts
    ?? app/src/test/kotlin/com/tjshea/vigilant/app/LaunchTraceTest.kt

## Last ten checkpoints
```
  cb2aa253e ckpt 4555: answered Tj: v1.0.127 diag read (idle slow 64->39%, Engine 94->25%); PF7 lo
  f942fb6b0 ckpt 4554: v1.0.125-127 shipped for tablet lag (baseline profile, Engine/Live timers)
  e220ce8af ckpt 4553: pre-release: Live tab timers slow down on low-RAM tablets (versionCode 467,
  6f4576aed ckpt 4552: pre-ship: Live tab timers slow down on low-RAM tablets
  710fa073a ckpt 4551: pre-release: Engine tab redraws every 5 s instead of every second (12 s on 
  5328bf0e8 ckpt 4550: pre-ship: Engine tab redraws every 5 s instead of every second (12 s on low
  4ba4b9f15 ckpt 4549: pre-release: Baseline profile: precompiles the app's code (tablet cold star
  bb8e3a59a ckpt 4548: pre-ship: Baseline profile: precompiles the app's code (tablet cold start 1
  8d0bbb0f5 ckpt 4547: PF6a baseline profile for tablet cold start
  eb4eeb371 ckpt 4546: answered Tj slow start/crash; PF6 logged from 20:15 diag
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
