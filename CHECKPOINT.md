# CHECKPOINT 4768 — read me first, then TASKS.md

**Written:** 2026-11-20T05:49:05Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `a9c3b0408` (this checkpoint is the commit after it)

## Just done
MK38: propPairsCashReserve (a prop-pair quote leaves $60 of free cash to the classes that trade); full suite 4491 green; analysis: which declined classes actually trade

## Do this next
06:03 wake: ONE reset deploys MK37+MK38; then rfq_set propPairsLive=true; watch live pairs quote + won/filled

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  0ad71f9a4 ckpt 4767: MK37 evidence: where fills come from (morning vs now, executed-trade win sh
  3abb65590 ckpt 4766: MK37: in-play same-game pairs (moneyline/spread + total) priced through the
  1d27b8673 ckpt 4765: MK36: decline 3+ leg parlays whose legs are all favourites (study: -20.6% o
  ed905ede7 ckpt 4764: MK31e: live fair current-as-of-now for stream-held books, watch up to 600 m
  2b82c5518 ckpt 4763: MK35: pregame 3+ leg parlays need a longshot leg (manyLegsNeedLongshot); ba
  93e4e8541 ckpt 4762: MK33c: study adds same-game segments; same-game 2-leg parlays +8% on the 11
  e974a4fc4 ckpt 4761: MK33b: rfq_pnl_study tool + daily workflow + research note: maker P&L at th
  c63a3bb19 ckpt 4760: MK34: maxLegFair .5 after 1 win 15 losses (-58.60)
  380c09355 ckpt 4760: MK32: settled maker parlays show adverse selection (31 of 32 legs won vs 23
  03264fefb ckpt 4759: MK31d: live legs up to 99% (liveMaxLegFair): 1,500 of 1,938 priced in-play 
```

(8 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
