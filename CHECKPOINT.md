# CHECKPOINT 4771 — read me first, then TASKS.md

**Written:** 2026-11-20T06:45:13Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `b62013148` (this checkpoint is the commit after it)

## Just done
MK42: floors halved (margin .003, fairErrorRel .003, minPoints .001, minReturn .002); engine restarted 06:44 (job 44689559689) with MK40/MK41 coverage fixes

## Do this next
read 'RFQ ladder' + 'RFQ prop coverage' log lines, then quotes/won/filled; judge the thinner margin after 20 fills; no reset before 07:45

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  be280dcae ckpt 4770: MK40/MK41: coverage fixes (protected prop targets, Yes-only anytime-TD list
  e93e1ba3a ckpt 4769: MK39: maxStake 12 (a code default, not Tj's) -> 100, walletShare .75, liveM
  3d1d963bc ckpt 4768: MK38: propPairsCashReserve (a prop-pair quote leaves $60 of free cash to th
  0ad71f9a4 ckpt 4767: MK37 evidence: where fills come from (morning vs now, executed-trade win sh
  3abb65590 ckpt 4766: MK37: in-play same-game pairs (moneyline/spread + total) priced through the
  1d27b8673 ckpt 4765: MK36: decline 3+ leg parlays whose legs are all favourites (study: -20.6% o
  ed905ede7 ckpt 4764: MK31e: live fair current-as-of-now for stream-held books, watch up to 600 m
  2b82c5518 ckpt 4763: MK35: pregame 3+ leg parlays need a longshot leg (manyLegsNeedLongshot); ba
  93e4e8541 ckpt 4762: MK33c: study adds same-game segments; same-game 2-leg parlays +8% on the 11
  e974a4fc4 ckpt 4761: MK33b: rfq_pnl_study tool + daily workflow + research note: maker P&L at th
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
