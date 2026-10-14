# CHECKPOINT 3210 — read me first, then TASKS.md

**Written:** 2026-10-14T03:08:53Z · **tests:** all 4 fast checks green
**Branch:** `ccr-147419fb-llvuq5` · **builds on:** `c56ea3e98` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.99.0: Live light: a push lane that finds obviously wrong live prices from the score and clock (Novig websocket + ESPN, BoltOdds soft books as a second opinion), bets them at once and, with its own switch, bids far under the model's fair

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M app/build.gradle.kts
     M app/src/main/kotlin/com/tjshea/vigilant/app/TailText.kt

## Last ten checkpoints
```
  40e3c08e2 ckpt 3209: TLL1-3: Live light push lane built (LiveLightScan, LiveLightRunner, BoltLiv
  85db7bf22 ckpt 3208: TLL2/TLL3 in progress: LiveLightScan (score model), TailOffers extracted, L
  171bb4e1a ckpt 3207: v0.98.1 recorded; v0.98.2 (line-stack guard for the auto-bet) shipped, rele
  1f7ab6693 ckpt 3206: pre-release: v0.98.2: the auto-bet holds one line of a team's spread (or a 
  040d8b6e2 ckpt 3205: pre-ship: v0.98.2: the auto-bet holds one line of a team's spread (or a pla
  b1955036c ckpt 3204: v0.98.1 shipped, release.yml triggered (list fix + bid skip reasons)
  a2bf54905 ckpt 3203: pre-release: v0.98.1: the CNO list drops a bet the Bet sheet or the lowest 
  e570ee2da ckpt 3202: pre-ship: v0.98.1: the CNO list drops a bet the Bet sheet or the lowest fai
  bcf471faa ckpt 3201: v0.98.0 released and recorded
  13cdd2f2f ckpt 3200: pre-release: v0.98.0: bids keep the wallet working (stakes scale to 1/4 Kel
```

(8 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
