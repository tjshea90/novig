# CHECKPOINT 4087 — read me first, then TASKS.md

**Written:** 2026-10-24T01:36:19Z · **tests:** all 9 fast checks green
**Branch:** `ccr-305d3f7a-i2z8ja` · **builds on:** `6ba9c7de4` (this checkpoint is the commit after it)

## Just done
pre-ship: SR: star rating re-weighted (sharp book 25->15, edge 25->30, agreement 20->25, type-of-bet points at half; CLV/star +0.50->+0.77, ROI/star +5.0->+6.2 on 1,968 graded engine lines); OK3: SharpAPI no longer re-reads NBA/NFL/NCAAF whole every minute while its stream is live

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  6ba9c7de4 ckpt 4086: SR1-2: verified the cut-off session's SR weights against review-digest run 
  fa7239519 ckpt 4085: OK1-3: The Odds API credits corrected/explained (per-key daily pace, no cre
  9c7198ea1 ckpt 4084: Corrected The Odds API claim (Tj's screenshot): 5,082 of 10,500 credits lef
  27b505acd ckpt 4083: SR1: BetRating.Weights (CURRENT = ST1, unchanged behaviour) + StarCheck sco
  918d506c9 ckpt 4082: v1.0.66 released and recorded; engine on the new main (run 345): Kalshi can
  fc85b89f5 ckpt 4081: BX done: BoltOdds no longer earns runner swaps (out of ApiManager.IMPORTANT
  2b53d0fe3 ckpt 4080: pre-release: FX full tests: Kalshi bid cancels fixed (V2 needs the market),
  40c94935b ckpt 4079: FX3/FX4 written: research/star_rating_review_2026-10-23.md, research/api_ve
  8e828381f ckpt 4078: FX2/FX3: star check rerun read (recorded lines only: ROI rises with stars, 
  d492cbeef ckpt 4077: FX2: SharpAPI and SX Bet streams ignored server closes (no onClosing): a de
```
