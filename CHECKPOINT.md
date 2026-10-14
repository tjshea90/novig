# CHECKPOINT 3221 — read me first, then TASKS.md

**Written:** 2026-10-14T05:39:52Z · **tests:** all 4 fast checks green
**Branch:** `ccr-147419fb-llvuq5` · **builds on:** `debd9aea9` (this checkpoint is the commit after it)

## Just done
pre-release: v0.99.3: Live light loosened (model fair 90%, bids 7% under the fair from 85% sure, curve bets from 55%/6%) and the lag fixed (market list read once in 90 s, steady stream subscriptions, status published twice a second, Bids-tab section refreshes only while shown) (versionCode 262, v0.99.3)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.99.3), then run: bash tools/record-release.sh v0.99.3 262 "v0.99.3: Live light loosened (model fair 90%, bids 7% under the fair from 85% sure, curve bets from 55%/6%) and the lag fixed (market list read once in 90 s, steady stream subscriptions, status published twice a second, Bids-tab section refreshes only while shown)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  debd9aea9 ckpt 3220: pre-ship: v0.99.3: Live light loosened (model fair 90%, bids 7% under the f
  ff90d7ec2 ckpt 3219: v0.99.2 released and recorded
  65f736872 ckpt 3218: pre-release: v0.99.2: Live light shows on the Bids tab (status, one line pe
  0b7132df8 ckpt 3217: pre-ship: v0.99.2: Live light shows on the Bids tab (status, one line per w
  da7b68cbe ckpt 3216: v0.99.1 released and recorded
  1c82d54be ckpt 3215: v0.99.1 shipped (alternate-line watch + ladder curve), release.yml triggere
  f1bb4a157 ckpt 3214: pre-release: v0.99.1: Live light watches alternate spreads and totals near 
  87a46b055 ckpt 3213: pre-ship: v0.99.1: Live light watches alternate spreads and totals near whe
  cc0927796 ckpt 3212: v0.99.0 Live light shipped (bets + bids), release.yml triggered; v0.98.2 re
  6c8afde16 ckpt 3211: pre-release: v0.99.0: Live light: a push lane that finds obviously wrong li
```
