# CHECKPOINT 3205 — read me first, then TASKS.md

**Written:** 2026-10-14T01:48:13Z · **tests:** all 4 fast checks green
**Branch:** `ccr-147419fb-llvuq5` · **builds on:** `4c288aa50` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.98.2: the auto-bet holds one line of a team's spread (or a player's side) per game, counting open bets and resting bids (Settings > Betting: Most other lines of the same team or player in one game)

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  b1955036c ckpt 3204: v0.98.1 shipped, release.yml triggered (list fix + bid skip reasons)
  a2bf54905 ckpt 3203: pre-release: v0.98.1: the CNO list drops a bet the Bet sheet or the lowest 
  e570ee2da ckpt 3202: pre-ship: v0.98.1: the CNO list drops a bet the Bet sheet or the lowest fai
  bcf471faa ckpt 3201: v0.98.0 released and recorded
  13cdd2f2f ckpt 3200: pre-release: v0.98.0: bids keep the wallet working (stakes scale to 1/4 Kel
  55278ff8e ckpt 3199: pre-ship: v0.98.0: bids keep the wallet working (stakes scale to 1/4 Kelly,
  150d1c5bf ckpt 3198: TWF5: diagnostics sockets, wallet deployment, stream phase timings, lab yie
  ccf9dce4a ckpt 3197: TWF4: bids keep the wallet working (stake scale to 1/4 Kelly, riskier highe
  4767443bc ckpt 3196: TWF2: public lookups slow on 429 + remembered, lab yields to scans, stream 
  2f4a16aaa ckpt 3195: TWF tasks written
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
