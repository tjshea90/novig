# CHECKPOINT 2228 — read me first, then TASKS.md

**Written:** 2026-10-01T01:40:44Z · **tests:** all 3 fast checks green
**Branch:** `ccr-01f0c1f9-xy73ag` · **builds on:** `ded549e9` (this checkpoint is the commit after it)

## Just done
Z1: real ParlayAPI tennis read (10 credits): Pinnacle match event = set lines, '(Games)' event = its games lines, other books' match event = games (BetMGM/DK use ±1.5 for games), 28/157 matches split over events, doubles mixed in; Novig lists SET_SPREAD/TOTAL_SETS; fixtures parlay-tennis-{atp,wta}.json, PARLAY_API.md §6.11

## Do this next
Z2: TennisLines.normalize (merge split events, drop doubles, Pinnacle match-event lines -> PERIOD_SETS, (Games) -> games), PARLAY supports tennis (3 credits a tour, no alternates), Planner SET_SPREAD/TOTAL_SETS, tests

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M PARLAY_API.md

## Last ten checkpoints
```
  b97184ba ckpt 2227: Logged Tj's 'Build tennis through parlayapi' as TASKS.md Z1-Z5 (W5 accepted)
  e9dd8b66 ckpt 2226: v0.36.2 (code 66) released and recorded: Y1-Y5 done (Kelly in bet slips, Kel
  2c501b03 ckpt 2225: pre-release: v0.36.2: Kelly amount in every bet slip (in-app Bet sheet and N
  1e0d0d39 ckpt 2224: Y4 full tests done: floor green 1,279 (22 live skipped), 91 screenshots OK, 
  140c86fa ckpt 2223: Y3: Check odds now progress and Tracker saves throttled into the screen (300
  6b8ef3ce ckpt 2222: Y1-Y2: Bet sheet follows the Kelly slip setting (per-bet Kelly to the cent, 
  42427dc5 ckpt 2221: Logged Tj's 2026-10-01 request as TASKS.md Y1-Y5 (Kelly in bet slips + math,
  be961a80 ckpt 2220: v0.36.1 (code 65) released and recorded: X1-X3 done (mid-scan lag/crash)
  7a8e815a ckpt 2219: pre-release: v0.36.1: no more lag and crash switching tabs mid-scan (the scr
  aa28a1c8 ckpt 2218: X1/X2: scan and usage mirrors throttled (350 ms / 1 s), feed rebuilt only on
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
