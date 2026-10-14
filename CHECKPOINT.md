# CHECKPOINT 3243 — read me first, then TASKS.md

**Written:** 2026-10-14T18:09:02Z · **tests:** all 4 fast checks green
**Branch:** `ccr-5fc41785-3m8rjr` · **builds on:** `abe991140` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.99.10: wallet-filling bids may rest far behind the best bid, and Diagnostics says why the week-ahead pass posted nothing

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M app/build.gradle.kts
    ?? research/bids_lead_time_2026-10-14.md

## Last ten checkpoints
```
  767ab0abe ckpt 3242: TWS: logged Tj's 'few bids after a full scan' report; no v0.99.8 diagnostic
  4e433b62b ckpt 3241: v0.99.9 shipped (TNR1 requote during scan), release.yml triggered
  1fdb09d4d ckpt 3240: pre-release: v0.99.9: the +EV list re-prices its bets from Novig's live pri
  d8aae8d11 ckpt 3239: pre-ship: v0.99.9: the +EV list re-prices its bets from Novig's live prices
  ae43c57f5 ckpt 3238: v0.99.8 released and recorded (TWR2 done)
  4702f8911 ckpt 3237: v0.99.8 shipped, release.yml triggered
  173d62fd8 ckpt 3236: pre-release: v0.99.8: the auto bids read a week of games, so the weekend's 
  b99774136 ckpt 3235: pre-ship: v0.99.8: the auto bids read a week of games, so the weekend's NFL
  dddadc69a ckpt 3234: v0.99.7 recorded; week-ahead scan reach for bids done + MakerTest fixed (TW
  fbc986247 ckpt 3233: pre-release: v0.99.7: scan errors about Novig (the key stand-down, a failed
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
