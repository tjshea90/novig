# CHECKPOINT 3262 — read me first, then TASKS.md

**Written:** 2026-10-14T20:06:41Z · **tests:** all 4 fast checks green
**Branch:** `ccr-5fc41785-3m8rjr` · **builds on:** `fb7fb0081` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.99.14: Volume mode for bids - EV floors 1.5% safest / 2.5% riskier, up to 3,000 bids, the kinds that fill fastest first (from 24,000 bids of history), no stake scale-up

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  b06707bd8 ckpt 3261: v0.99.13 released and recorded (TWP2 done)
  990ebf93d ckpt 3260: v0.99.13 shipped (bid while scanning + FarLane quick scans), release.yml tr
  2793ac247 ckpt 3259: pre-release: v0.99.13: bids go up as a league's odds arrive during a scan, 
  956b73ce3 ckpt 3258: pre-ship: v0.99.13: bids go up as a league's odds arrive during a scan, and
  2dec5398e ckpt 3257: pre-release: v0.99.13: bids go up on a league as its odds arrive while a hu
  fc5c08769 ckpt 3256: pre-ship: v0.99.13: bids go up on a league as its odds arrive while a huge 
  ac4ad8106 ckpt 3255: v0.99.12 released and recorded (TWD3 done)
  d75e15dee ckpt 3254: v0.99.12 shipped (bids rest longer, transient-skip keep, per-game bid caps 
  a3a251768 ckpt 3253: pre-release: v0.99.12: bids rest longer (a transient old fair no longer tak
  df05b1517 ckpt 3252: pre-ship: v0.99.12: bids rest longer (a transient old fair no longer takes 
```

(8 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
