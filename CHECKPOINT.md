# CHECKPOINT 3214 — read me first, then TASKS.md

**Written:** 2026-10-14T03:20:21Z · **tests:** all 4 fast checks green
**Branch:** `ccr-147419fb-llvuq5` · **builds on:** `87a46b055` (this checkpoint is the commit after it)

## Just done
pre-release: v0.99.1: Live light watches alternate spreads and totals near where the game is going and fits each ladder to a normal curve, so an alternate line priced out of line with its neighbours is bet too (versionCode 260, v0.99.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.99.1), then run: bash tools/record-release.sh v0.99.1 260 "v0.99.1: Live light watches alternate spreads and totals near where the game is going and fits each ladder to a normal curve, so an alternate line priced out of line with its neighbours is bet too"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  87a46b055 ckpt 3213: pre-ship: v0.99.1: Live light watches alternate spreads and totals near whe
  cc0927796 ckpt 3212: v0.99.0 Live light shipped (bets + bids), release.yml triggered; v0.98.2 re
  6c8afde16 ckpt 3211: pre-release: v0.99.0: Live light: a push lane that finds obviously wrong li
  a95505eba ckpt 3210: pre-ship: v0.99.0: Live light: a push lane that finds obviously wrong live 
  40e3c08e2 ckpt 3209: TLL1-3: Live light push lane built (LiveLightScan, LiveLightRunner, BoltLiv
  85db7bf22 ckpt 3208: TLL2/TLL3 in progress: LiveLightScan (score model), TailOffers extracted, L
  171bb4e1a ckpt 3207: v0.98.1 recorded; v0.98.2 (line-stack guard for the auto-bet) shipped, rele
  1f7ab6693 ckpt 3206: pre-release: v0.98.2: the auto-bet holds one line of a team's spread (or a 
  040d8b6e2 ckpt 3205: pre-ship: v0.98.2: the auto-bet holds one line of a team's spread (or a pla
  b1955036c ckpt 3204: v0.98.1 shipped, release.yml triggered (list fix + bid skip reasons)
```
