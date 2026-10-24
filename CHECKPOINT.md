# CHECKPOINT 4086 — read me first, then TASKS.md

**Written:** 2026-10-24T01:31:43Z · **tests:** all 9 fast checks green
**Branch:** `ccr-305d3f7a-i2z8ja` · **builds on:** `55d8b794b` (this checkpoint is the commit after it)

## Just done
SR1-2: verified the cut-off session's SR weights against review-digest run 40047913906 (CLV/star +0.77 vs ST1 +0.50, ROI/star +6.2 vs +5.0, 4-5★ +1.83% CLV +2.81% ROI); SR section in star_rating_review, LEVERS, CHANGES (revert rule), export READ ME; BetRatingTest+AutoBetStarsTest green

## Do this next
SR3: full suite (bash tools/test.sh), ship.sh v1.0.67 with SR + OK3 (SharpAPI reconcile fix), release.yml, record-release, engine reset (engine_set.py --reset), tell Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  fa7239519 ckpt 4085: OK1-3: The Odds API credits corrected/explained (per-key daily pace, no cre
  9c7198ea1 ckpt 4084: Corrected The Odds API claim (Tj's screenshot): 5,082 of 10,500 credits lef
  27b505acd ckpt 4083: SR1: BetRating.Weights (CURRENT = ST1, unchanged behaviour) + StarCheck sco
  918d506c9 ckpt 4082: v1.0.66 released and recorded; engine on the new main (run 345): Kalshi can
  fc85b89f5 ckpt 4081: BX done: BoltOdds no longer earns runner swaps (out of ApiManager.IMPORTANT
  2b53d0fe3 ckpt 4080: pre-release: FX full tests: Kalshi bid cancels fixed (V2 needs the market),
  40c94935b ckpt 4079: FX3/FX4 written: research/star_rating_review_2026-10-23.md, research/api_ve
  8e828381f ckpt 4078: FX2/FX3: star check rerun read (recorded lines only: ROI rises with stars, 
  d492cbeef ckpt 4077: FX2: SharpAPI and SX Bet streams ignored server closes (no onClosing): a de
  59e69cd79 ckpt 4076: FX2: study splits capped at 25 groups (engine digest's Books agreeing had h
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
