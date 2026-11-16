# CHECKPOINT 4560 — read me first, then TASKS.md

**Written:** 2026-11-16T21:24:23Z · **tests:** all 10 fast checks green
**Branch:** `claude/optimistic-curie-cnhej8` · **builds on:** `b9e61e59e` (this checkpoint is the commit after it)

## Just done
pre-release: Chat typing no longer redraws the whole thread on every key; finer launch trace (versionCode 469, v1.0.129)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.129), then run: bash tools/record-release.sh v1.0.129 469 "Chat typing no longer redraws the whole thread on every key; finer launch trace"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  b9e61e59e ckpt 4559: pre-ship: Chat typing no longer redraws the whole thread on every key; fine
  564dfebcf ckpt 4558: PF8: launch trace stages; v1.0.128 building
  d87e8240b ckpt 4557: pre-release: Launch trace: the next report says how far a run got before An
  b3931b43d ckpt 4556: pre-ship: Launch trace: the next report says how far a run got before Andro
  cb2aa253e ckpt 4555: answered Tj: v1.0.127 diag read (idle slow 64->39%, Engine 94->25%); PF7 lo
  f942fb6b0 ckpt 4554: v1.0.125-127 shipped for tablet lag (baseline profile, Engine/Live timers)
  e220ce8af ckpt 4553: pre-release: Live tab timers slow down on low-RAM tablets (versionCode 467,
  6f4576aed ckpt 4552: pre-ship: Live tab timers slow down on low-RAM tablets
  710fa073a ckpt 4551: pre-release: Engine tab redraws every 5 s instead of every second (12 s on 
  5328bf0e8 ckpt 4550: pre-ship: Engine tab redraws every 5 s instead of every second (12 s on low
```
