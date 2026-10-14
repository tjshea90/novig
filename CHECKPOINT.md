# CHECKPOINT 3264 — read me first, then TASKS.md

**Written:** 2026-10-14T20:15:00Z · **tests:** all 4 fast checks green
**Branch:** `ccr-5fc41785-3m8rjr` · **builds on:** `51040dd2b` (this checkpoint is the commit after it)

## Just done
pre-release: v0.99.14: Volume mode for bids - EV floors 1.5% safest / 2.5% riskier, up to 3,000 bids, the kinds that fill fastest first (from 24,000 bids of history), no stake scale-up (versionCode 273, v0.99.14)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.99.14), then run: bash tools/record-release.sh v0.99.14 273 "v0.99.14: Volume mode for bids - EV floors 1.5% safest / 2.5% riskier, up to 3,000 bids, the kinds that fill fastest first (from 24,000 bids of history), no stake scale-up"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  51040dd2b ckpt 3263: pre-ship: v0.99.14: Volume mode for bids - EV floors 1.5% safest / 2.5% ris
  4c3849b70 ckpt 3262: pre-ship: v0.99.14: Volume mode for bids - EV floors 1.5% safest / 2.5% ris
  b06707bd8 ckpt 3261: v0.99.13 released and recorded (TWP2 done)
  990ebf93d ckpt 3260: v0.99.13 shipped (bid while scanning + FarLane quick scans), release.yml tr
  2793ac247 ckpt 3259: pre-release: v0.99.13: bids go up as a league's odds arrive during a scan, 
  956b73ce3 ckpt 3258: pre-ship: v0.99.13: bids go up as a league's odds arrive during a scan, and
  2dec5398e ckpt 3257: pre-release: v0.99.13: bids go up on a league as its odds arrive while a hu
  fc5c08769 ckpt 3256: pre-ship: v0.99.13: bids go up on a league as its odds arrive while a huge 
  ac4ad8106 ckpt 3255: v0.99.12 released and recorded (TWD3 done)
  d75e15dee ckpt 3254: v0.99.12 shipped (bids rest longer, transient-skip keep, per-game bid caps 
```
