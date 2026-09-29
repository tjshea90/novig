# CHECKPOINT 2080 — read me first, then TASKS.md

**Written:** 2026-09-29T16:54:18Z · **tests:** all 2 fast checks green
**Branch:** `ccr-538c37db-finzj9` · **builds on:** `d43615e2` (this checkpoint is the commit after it)

## Just done
X1 done (RESEARCH.md §38, TASKS X2-X4 broken into steps); X2a Scanner(betsOnly); X2b TrackedBet.nowVia/nowNote, BetTracker.applyPricing/applyFair, observe advances the age, CNO recheck sets nowVia

## Do this next
X2c: BetsScope (settings for the bets), BetPricingReasons.explain, OpenBetPricer; then X2d wiring in checkOdds; compile first: bash tools/test.sh :data:test --tests '*BetTrackerTest'

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  cc813115 ckpt 2079: Recorded Tj's tracker request (recheck every open bet incl. Vigilant scanner
  4101c936 ckpt 2078: v0.21.0 (code 46) released and recorded; W9 ticked
  57fa81d3 ckpt 2077: pre-release: v0.21.0: bet through Novig's API from a separate Vigilant walle
  535e01eb ckpt 2076: W9: placement never cancelled by closing the sheet (+test that failed withou
  a9194471 ckpt 2075: ApiSettler: bets sharing a market are graded together (one market payout no 
  32c7dac5 ckpt 2074: W1-W8 ticked; RESEARCH.md §36.6 (MoneyLine tested) and §37 (API betting de
  2741c198 ckpt 2073: W6-W8 app layer done: connection store + container wiring, ApiBettingControl
  445ddb62 ckpt 2072: W6/W7 data: NovigBettingSetup (use the phone's trading key or revoke+mint, f
  6c651d47 ckpt 2071: W4/W5 data layer: NovigTradingClient, ApiBetPlanner, ApiBetPlacer (IOC at th
  8c20dd5a ckpt 2070: W1/W2 done (MoneyLine tested: stale ~2 h, no Pinnacle, 3 MB for 3 events); N
```

(12 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
