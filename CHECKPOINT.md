# CHECKPOINT 4092 — read me first, then TASKS.md

**Written:** 2026-10-24T02:11:10Z · **tests:** all 9 fast checks green
**Branch:** `ccr-305d3f7a-i2z8ja` · **builds on:** `8b0c5e816` (this checkpoint is the commit after it)

## Just done
pre-release: S5: star scale fixed at the bottom: 0 = no trusted edge (EV under 0.5% or over 25%, or a sharp book against: these lost to the close), under 1% EV = 1 star; every rated level (1-5) beat the close; star check judges on Pinnacle/Circa closes over games (versionCode 408, v1.0.68)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.68), then run: bash tools/record-release.sh v1.0.68 408 "S5: star scale fixed at the bottom: 0 = no trusted edge (EV under 0.5% or over 25%, or a sharp book against: these lost to the close), under 1% EV = 1 star; every rated level (1-5) beat the close; star check judges on Pinnacle/Circa closes over games"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  8b0c5e816 ckpt 4091: pre-ship: S5: star scale fixed at the bottom: 0 = no trusted edge (EV under
  8f5d90a9b ckpt 4090: S5a-c: star scale checked on the engine's 20,840 graded lines; 0 = no trust
  f0ac263e3 ckpt 4089: v1.0.67 gated (full suite 4,207 green) and on main; release.yml triggered
  4567b1e3b ckpt 4088: pre-release: SR: star rating re-weighted (sharp book 25->15, edge 25->30, a
  81e2270b7 ckpt 4087: pre-ship: SR: star rating re-weighted (sharp book 25->15, edge 25->30, agre
  6ba9c7de4 ckpt 4086: SR1-2: verified the cut-off session's SR weights against review-digest run 
  fa7239519 ckpt 4085: OK1-3: The Odds API credits corrected/explained (per-key daily pace, no cre
  9c7198ea1 ckpt 4084: Corrected The Odds API claim (Tj's screenshot): 5,082 of 10,500 credits lef
  27b505acd ckpt 4083: SR1: BetRating.Weights (CURRENT = ST1, unchanged behaviour) + StarCheck sco
  918d506c9 ckpt 4082: v1.0.66 released and recorded; engine on the new main (run 345): Kalshi can
```
