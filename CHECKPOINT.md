# CHECKPOINT 3436 — read me first, then TASKS.md

**Written:** 2026-10-16T16:35:28Z · **tests:** all 5 fast checks green
**Branch:** `ccr-6cce5eff-xcxliy` · **builds on:** `de1e45248` (this checkpoint is the commit after it)

## Just done
TCL45a: Kalshi key to GitHub secrets, engine signer + socket; app sends it

## Do this next
release app (Kalshi send), engine restart, BoltOdds docs review TCL45b, chat context TCL46

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M CLAUDE.md
     M TASKS.md
     M cloud/src/main/kotlin/com/tjshea/vigilant/cloud/CloudMain.kt

## Last ten checkpoints
```
  d822fa174 ckpt 3435: engine start log shows how many keys each secret holds
  4ee06d0a5 ckpt 3434: pre-release: v0.114.5: the chat refreshes the moment the app comes back aft
  e778af458 ckpt 3433: pre-ship: v0.114.5: the chat refreshes the moment the app comes back after 
  87e39ba2e ckpt 3432: TCL43: bolt multi-key already supported; answered
  3e5964d35 ckpt 3431: TCL42a: chat loop wakes on app resume
  16cbd85f8 ckpt 3430: engine run 55 live: bids resume (494 resting); Bolt still 429
  304c35bf3 ckpt 3429: TCL40\/41 answered; engine restarted on new code (Bolt retry, bid guard 1h\
  b5be1a9d0 ckpt 3428: TCL40: Bolt snapshot retries in 60s + 429 detail; engine bid guard pauses 1
  7db014870 ckpt 3427: TCL39a: SGO 403 'Inactive API key' now refuses the key (rotation + advisor 
  60f49130c ckpt 3426: TCL38: answered why no bids (picked-off pause, SGO key inactive)
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
