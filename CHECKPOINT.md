# CHECKPOINT 4090 — read me first, then TASKS.md

**Written:** 2026-10-24T02:03:28Z · **tests:** all 9 fast checks green
**Branch:** `ccr-305d3f7a-i2z8ja` · **builds on:** `ec778b5e8` (this checkpoint is the commit after it)

## Just done
S5a-c: star scale checked on the engine's 20,840 graded lines; 0 = no trusted edge (EV <0.5% / >25% / sharp against: these lost to the close), 1★ under 1%; SR weights kept (no weighting orders rated lines, |t|<1); StarCheck judges sharp closes over games; research/star_rating_s5_2026-10-24.md

## Do this next
S5d: full suite (bash tools/test.sh), bump 408/1.0.68, ship.sh, release.yml, record-release, engine reset, tell Tj what each star means

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M research/INDEX.md
     M research/LEVERS.md
     M research/review/CHANGES.md

## Last ten checkpoints
```
  f0ac263e3 ckpt 4089: v1.0.67 gated (full suite 4,207 green) and on main; release.yml triggered
  4567b1e3b ckpt 4088: pre-release: SR: star rating re-weighted (sharp book 25->15, edge 25->30, a
  81e2270b7 ckpt 4087: pre-ship: SR: star rating re-weighted (sharp book 25->15, edge 25->30, agre
  6ba9c7de4 ckpt 4086: SR1-2: verified the cut-off session's SR weights against review-digest run 
  fa7239519 ckpt 4085: OK1-3: The Odds API credits corrected/explained (per-key daily pace, no cre
  9c7198ea1 ckpt 4084: Corrected The Odds API claim (Tj's screenshot): 5,082 of 10,500 credits lef
  27b505acd ckpt 4083: SR1: BetRating.Weights (CURRENT = ST1, unchanged behaviour) + StarCheck sco
  918d506c9 ckpt 4082: v1.0.66 released and recorded; engine on the new main (run 345): Kalshi can
  fc85b89f5 ckpt 4081: BX done: BoltOdds no longer earns runner swaps (out of ApiManager.IMPORTANT
  2b53d0fe3 ckpt 4080: pre-release: FX full tests: Kalshi bid cancels fixed (V2 needs the market),
```

(12 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
