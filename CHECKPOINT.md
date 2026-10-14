# CHECKPOINT 3252 — read me first, then TASKS.md

**Written:** 2026-10-14T18:59:47Z · **tests:** all 4 fast checks green
**Branch:** `ccr-5fc41785-3m8rjr` · **builds on:** `fe2232570` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.99.12: bids rest longer (a transient old fair no longer takes a bid down; wallet-filling bids on far games live up to 30 min), and are spread over many games and kinds with a per-game bid limit (default 10 bids, $10)

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/src/main/kotlin/com/tjshea/vigilant/app/ui/MakerScreen.kt
     M app/src/test/kotlin/com/tjshea/vigilant/app/MakerUiTest.kt
     M data/src/main/kotlin/com/tjshea/vigilant/data/novig/trading/maker/MakerQuote.kt
     M data/src/main/kotlin/com/tjshea/vigilant/data/scanner/ScanSettings.kt

## Last ten checkpoints
```
  fe2232570 ckpt 3251: pre-ship: v0.99.12: bids rest longer (a transient old fair no longer takes 
  bf53370a0 ckpt 3250: v0.99.11 released and recorded (TWF3 done)
  66182769d ckpt 3249: v0.99.10 recorded; v0.99.11 shipped (last wallet-filling pass: any time/kin
  ef8e9ae74 ckpt 3248: pre-release: v0.99.11: the last wallet-filling pass bids every start time a
  1093b87e9 ckpt 3247: pre-ship: v0.99.11: the last wallet-filling pass bids every start time and 
  8afb8b263 ckpt 3246: pre-ship: v0.99.11: the last wallet-filling pass bids every start time and 
  2b98e8d05 ckpt 3245: v0.99.9 recorded; v0.99.10 shipped (wallet-filling bids ignore max-behind, 
  1d5882246 ckpt 3244: pre-release: v0.99.10: wallet-filling bids may rest far behind the best bid
  17c56d7d7 ckpt 3243: pre-ship: v0.99.10: wallet-filling bids may rest far behind the best bid, a
  767ab0abe ckpt 3242: TWS: logged Tj's 'few bids after a full scan' report; no v0.99.8 diagnostic
```
