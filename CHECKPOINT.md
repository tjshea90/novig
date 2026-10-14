# CHECKPOINT 3246 — read me first, then TASKS.md

**Written:** 2026-10-14T18:16:00Z · **tests:** all 4 fast checks green
**Branch:** `ccr-5fc41785-3m8rjr` · **builds on:** `56455fc30` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.99.11: the last wallet-filling pass bids every start time and kind with no sharp book needed (popular first, riskier kinds at a higher edge), and bids read ten days ahead

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M app/build.gradle.kts
     M data/src/test/kotlin/com/tjshea/vigilant/data/novig/trading/maker/MakerTest.kt

## Last ten checkpoints
```
  2b98e8d05 ckpt 3245: v0.99.9 recorded; v0.99.10 shipped (wallet-filling bids ignore max-behind, 
  1d5882246 ckpt 3244: pre-release: v0.99.10: wallet-filling bids may rest far behind the best bid
  17c56d7d7 ckpt 3243: pre-ship: v0.99.10: wallet-filling bids may rest far behind the best bid, a
  767ab0abe ckpt 3242: TWS: logged Tj's 'few bids after a full scan' report; no v0.99.8 diagnostic
  4e433b62b ckpt 3241: v0.99.9 shipped (TNR1 requote during scan), release.yml triggered
  1fdb09d4d ckpt 3240: pre-release: v0.99.9: the +EV list re-prices its bets from Novig's live pri
  d8aae8d11 ckpt 3239: pre-ship: v0.99.9: the +EV list re-prices its bets from Novig's live prices
  ae43c57f5 ckpt 3238: v0.99.8 released and recorded (TWR2 done)
  4702f8911 ckpt 3237: v0.99.8 shipped, release.yml triggered
  173d62fd8 ckpt 3236: pre-release: v0.99.8: the auto bids read a week of games, so the weekend's 
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
