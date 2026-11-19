# CHECKPOINT 4688 — read me first, then TASKS.md

**Written:** 2026-11-19T16:23:30Z · **tests:** all 10 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `beb2dc50e` (this checkpoint is the commit after it)

## Just done
MK12b: same-game copula (RfqCorrelation: Gauss+SameGame, measured rho by league NBA/NFL/NHL/NCAAF/NCAAB) wired into RfqPricer; RfqMarket (probe of Novig keyless parlay price + per-class demand model + optimal margin) wired into RfqMaker/CloudMain; tests RfqCorrelationTest, RfqMarketTest, RfqMakerTest green

## Do this next
MK12c: rfq_set.py (sameGamePremium 0.02, probe, marketPricing, probeMinSamples), research doc, full test gate, land on main, engine reset + verify new job startedAtMs; then ladder/Novig-mid coverage

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M cloud/src/main/kotlin/com/tjshea/vigilant/cloud/CloudMain.kt

## Last ten checkpoints
```
  42f706e6b ckpt 4687: MK12a: fixed last-look price tolerance (Novig echoes 0.4495 as 0.450; the o
  e742cad0f ckpt 4686: MK10k: rfq maker SHADOW (paper) per Tj
  b7ef6b620 ckpt 4685: TR7 logged: ledger drop = parlay collateral
  c1e50755e ckpt 4684: MK10j: open collateral cap 80 (wallet cash 12.5)
  5eff872a6 ckpt 4683: WATCH 11-19 13:20 slot: engine healthy, parlay check no change
  082eeaabf ckpt 4682: MK10 11:08: fills starting (9 won, 8 filled)
  8624ed34e ckpt 4681: MK10i: rfq floor 0.7pt (minPoints .007 margin .015 minReturn .004)
  87e289ffe ckpt 4680: MK10 08:37 check: no change, evidence logged
  2da22662c ckpt 4679: MK10h: rfq minReturn 0.006
  135bf46a5 ckpt 4678: MK10g: rfq minReturn 0.01
```

(14 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
