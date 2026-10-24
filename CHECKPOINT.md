# CHECKPOINT 4083 — read me first, then TASKS.md

**Written:** 2026-10-24T01:02:05Z · **tests:** all 9 fast checks green
**Branch:** `ccr-f7c47823-ez8xwu` · **builds on:** `1655a8d25` (this checkpoint is the commit after it)

## Just done
SR1: BetRating.Weights (CURRENT = ST1, unchanged behaviour) + StarCheck scores 6 principled weight variants (type x1/x0.5/x0, sharp 25 vs 15 with edge 30/agree 25) on all lines and each half; cloud+BetRating tests green

## Do this next
dispatch review-digest.yml on this branch, read 'Weights side by side', adopt only a variant better on both halves

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
    ?? research/uploads/2026-10-24/

## Last ten checkpoints
```
  918d506c9 ckpt 4082: v1.0.66 released and recorded; engine on the new main (run 345): Kalshi can
  fc85b89f5 ckpt 4081: BX done: BoltOdds no longer earns runner swaps (out of ApiManager.IMPORTANT
  2b53d0fe3 ckpt 4080: pre-release: FX full tests: Kalshi bid cancels fixed (V2 needs the market),
  40c94935b ckpt 4079: FX3/FX4 written: research/star_rating_review_2026-10-23.md, research/api_ve
  8e828381f ckpt 4078: FX2/FX3: star check rerun read (recorded lines only: ROI rises with stars, 
  d492cbeef ckpt 4077: FX2: SharpAPI and SX Bet streams ignored server closes (no onClosing): a de
  59e69cd79 ckpt 4076: FX2: study splits capped at 25 groups (engine digest's Books agreeing had h
  5e21abe12 ckpt 4075: FX2 engine bugs from the live log: (1) Kalshi bid cancels sent by id alone 
  72184f30c ckpt 4074: FX2: StudyExport played-days-first (test fails on old code), Review day bud
  b8f282792 ckpt 4073: FX1 floor green (4,192 tests, exit 0), 25 py selftests ok, 134 screenshots 
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
