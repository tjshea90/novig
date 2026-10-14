# CHECKPOINT 3257 — read me first, then TASKS.md

**Written:** 2026-10-14T19:22:50Z · **tests:** all 4 fast checks green
**Branch:** `ccr-5fc41785-3m8rjr` · **builds on:** `fc5c08769` (this checkpoint is the commit after it)

## Just done
pre-release: v0.99.13: bids go up on a league as its odds arrive while a huge scan is still running (switch: Bids > Bid while the scan runs) (versionCode 272, v0.99.13)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.99.13), then run: bash tools/record-release.sh v0.99.13 272 "v0.99.13: bids go up on a league as its odds arrive while a huge scan is still running (switch: Bids > Bid while the scan runs)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  fc5c08769 ckpt 3256: pre-ship: v0.99.13: bids go up on a league as its odds arrive while a huge 
  ac4ad8106 ckpt 3255: v0.99.12 released and recorded (TWD3 done)
  d75e15dee ckpt 3254: v0.99.12 shipped (bids rest longer, transient-skip keep, per-game bid caps 
  a3a251768 ckpt 3253: pre-release: v0.99.12: bids rest longer (a transient old fair no longer tak
  df05b1517 ckpt 3252: pre-ship: v0.99.12: bids rest longer (a transient old fair no longer takes 
  fe2232570 ckpt 3251: pre-ship: v0.99.12: bids rest longer (a transient old fair no longer takes 
  bf53370a0 ckpt 3250: v0.99.11 released and recorded (TWF3 done)
  66182769d ckpt 3249: v0.99.10 recorded; v0.99.11 shipped (last wallet-filling pass: any time/kin
  ef8e9ae74 ckpt 3248: pre-release: v0.99.11: the last wallet-filling pass bids every start time a
  1093b87e9 ckpt 3247: pre-ship: v0.99.11: the last wallet-filling pass bids every start time and 
```
