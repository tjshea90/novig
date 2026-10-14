# CHECKPOINT 3208 — read me first, then TASKS.md

**Written:** 2026-10-14T02:58:21Z · **tests:** all 4 fast checks green
**Branch:** `ccr-147419fb-llvuq5` · **builds on:** `797e4ea13` (this checkpoint is the commit after it)

## Just done
TLL2/TLL3 in progress: LiveLightScan (score model), TailOffers extracted, LiveLightRunner (push lane) compile

## Do this next
write LiveLightScanTest + runner test, wire VigilantApp (start/stop in labTick, onTail yields when runner carries), settings toggle UI, diagnostics; then bids (TLL4)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  171bb4e1a ckpt 3207: v0.98.1 recorded; v0.98.2 (line-stack guard for the auto-bet) shipped, rele
  1f7ab6693 ckpt 3206: pre-release: v0.98.2: the auto-bet holds one line of a team's spread (or a 
  040d8b6e2 ckpt 3205: pre-ship: v0.98.2: the auto-bet holds one line of a team's spread (or a pla
  b1955036c ckpt 3204: v0.98.1 shipped, release.yml triggered (list fix + bid skip reasons)
  a2bf54905 ckpt 3203: pre-release: v0.98.1: the CNO list drops a bet the Bet sheet or the lowest 
  e570ee2da ckpt 3202: pre-ship: v0.98.1: the CNO list drops a bet the Bet sheet or the lowest fai
  bcf471faa ckpt 3201: v0.98.0 released and recorded
  13cdd2f2f ckpt 3200: pre-release: v0.98.0: bids keep the wallet working (stakes scale to 1/4 Kel
  55278ff8e ckpt 3199: pre-ship: v0.98.0: bids keep the wallet working (stakes scale to 1/4 Kelly,
  150d1c5bf ckpt 3198: TWF5: diagnostics sockets, wallet deployment, stream phase timings, lab yie
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
