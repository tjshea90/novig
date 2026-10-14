# CHECKPOINT 3199 — read me first, then TASKS.md

**Written:** 2026-10-14T01:02:38Z · **tests:** all 4 fast checks green
**Branch:** `ccr-b57fa2a2-ftkkrm` · **builds on:** `cc5c0bd7d` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.98.0: bids keep the wallet working (stakes scale to 1/4 Kelly, riskier higher-edge bids fill the gap), the paper lab waits for scans, Novig lookups slow on a 429 and are remembered, the streaming engine rests when BoltOdds' socket is off, Diagnostics gain SOCKETS, WALLET DEPLOYMENT and phase timings

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M CLAUDE.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  150d1c5bf ckpt 3198: TWF5: diagnostics sockets, wallet deployment, stream phase timings, lab yie
  ccf9dce4a ckpt 3197: TWF4: bids keep the wallet working (stake scale to 1/4 Kelly, riskier highe
  4767443bc ckpt 3196: TWF2: public lookups slow on 429 + remembered, lab yields to scans, stream 
  2f4a16aaa ckpt 3195: TWF tasks written
  0b6098311 ckpt 3194: v0.97.3 released and recorded
  e4819e974 ckpt 3193: v0.97.2 recorded; v0.97.3 (BoltOdds classify memo) shipped, release trigger
  4950d974f ckpt 3192: pre-release: v0.97.3: BoltOdds passes classify each market name once instea
  dc824e142 ckpt 3191: pre-ship: v0.97.3: BoltOdds passes classify each market name once instead o
  2d3b1c981 ckpt 3190: pre-release: v0.97.2: Kalshi socket now counts every subscribed market (ok 
  470cb4c06 ckpt 3189: pre-ship: v0.97.2: Kalshi socket now counts every subscribed market (ok ack
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
