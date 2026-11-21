# CHECKPOINT 4814 — read me first, then TASKS.md

**Written:** 2026-11-21T05:57:58Z · **tests:** all 11 fast checks green
**Branch:** `ccr-bc2a800c-fgp2ck` · **builds on:** `f101d012b` (this checkpoint is the commit after it)

## Just done
FV2: rfq profile widened on graded evidence (minFair .35->.25: .25-.35 band +9.0%/fill n1136; maxLeadMs 12h->24h; adaptiveCash on, score 5.7); in-play and <.25 fair NOT enabled (zero/negative); config live (rfq_set 2ce213043), CHANGES.md FV2 with revert

## Do this next
watch won/filled by class (fair band, lead bucket) over the next hours; revert line in CHANGES FV2; ON2 monitor check-ins continue; FV1a-d open

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M research/review/CHANGES.md

## Last ten checkpoints
```
  50c1e2292 ckpt 4813: BL1: delayed syncNow on resume (blurry return), compile checked
  11ef34574 ckpt 4813: FV1 analysis: research/parlay_fair_coverage_2026-11-21.md (weighted attribu
  d38120752 ckpt 4812: ON2 monitor check-in #2 (05:10 UTC): healthy; maker connected, not halted, 
  e76d99932 ckpt 4811: ON2 monitor check-in #1 (04:22 UTC): healthy; maker connected, not halted, 
  6480fc59e ckpt 4810: ON2 DEPLOYED: loss-stop fix live (run 44863668546: maker connected, not hal
  4a1fd76e7 ckpt 4809: ON2: RfqConfig.lossStopFromMs + RfqLedger.Summary.pnlStop24h: the maker's l
  f0f3752f8 ckpt 4808: ON1 DONE: engine LIVE (trading, bids via maker=true, scan on), desk resting
  c512d817d ckpt 4807: ON1: engine adopted gates.cloudTrading=LIVE (status tradeMode live); found 
  348e10fef ckpt 4806: ON1: engine_set gates.cloudTrading=LIVE gates.cloudBids=true vigGuards.bidD
  ba8689716 ckpt 4805: ENGINEER 11-20 routine: watch only, nothing to change
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
