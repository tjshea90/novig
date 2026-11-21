# CHECKPOINT 4816 — read me first, then TASKS.md

**Written:** 2026-11-21T06:05:28Z · **tests:** all 11 fast checks green
**Branch:** `ccr-bc2a800c-fgp2ck` · **builds on:** `8f25112ef` (this checkpoint is the commit after it)

## Just done
LP1: in-play parlays LIVE (liveParlays LIVE, per-leg cap .8, ~3 pt margin) on thin graded evidence (n120, EV rises with margin, 80-90% leg pocket -24% excluded); engine adopted 06:02:44; only 1 game in play so no in-play quote yet; rfq-pnl-study dispatched for a bigger sample

## Do this next
re-judge in-play margin when the study lands (Release rfq-pnl-study graded.csv.gz: live=1 EV at our quote by margin, need 300+ fills); watch first live in-play fills on the Saturday slate; revert in CHANGES LP1; ON2 monitor continues

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M research/review/CHANGES.md

## Last ten checkpoints
```
  5aa2d1d57 ckpt 4815: Handoff saved (.handoff/handoff.md + archive 2026-11-21T06-00); CLAUDE.md/A
  0091deef3 ckpt 4814: FV2: rfq profile widened on graded evidence (minFair .35->.25: .25-.35 band
  50c1e2292 ckpt 4813: BL1: delayed syncNow on resume (blurry return), compile checked
  11ef34574 ckpt 4813: FV1 analysis: research/parlay_fair_coverage_2026-11-21.md (weighted attribu
  d38120752 ckpt 4812: ON2 monitor check-in #2 (05:10 UTC): healthy; maker connected, not halted, 
  e76d99932 ckpt 4811: ON2 monitor check-in #1 (04:22 UTC): healthy; maker connected, not halted, 
  6480fc59e ckpt 4810: ON2 DEPLOYED: loss-stop fix live (run 44863668546: maker connected, not hal
  4a1fd76e7 ckpt 4809: ON2: RfqConfig.lossStopFromMs + RfqLedger.Summary.pnlStop24h: the maker's l
  f0f3752f8 ckpt 4808: ON1 DONE: engine LIVE (trading, bids via maker=true, scan on), desk resting
  c512d817d ckpt 4807: ON1: engine adopted gates.cloudTrading=LIVE (status tradeMode live); found 
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
