# CHECKPOINT 4693 — read me first, then TASKS.md

**Written:** 2026-11-19T16:54:38Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `8e41383f8` (this checkpoint is the commit after it)

## Just done
MK12f: model floor fairErrorRel (fair x 2% x sqrt(legs)), xs band, via-quotes out of the evidence counters, rfq-market.yml (free workflow: rfq_market.py table every 6 h into the rfq-market Release), size-of-the-prize and fix-ups in the research doc; FULL suite green (4428 tests)

## Do this next
land, add min-wins guard to RfqMarketModel.best, walk-forward table in the doc, then redeploy (engine_set --reset) and read the first probe table

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  12977b6eb ckpt 4692: MK12e: SHADOW capital fix, propPairsFromMarket (OFF), Parlays-view probe/mo
  6d511ca17 ckpt 4691: MK11: RfqLadder prop rung interpolation wired into the rfq leg fairs
  c4ae5ea25 ckpt 4691: MK12d: SHADOW judges RFQs as if the wallet were free (capital cap only in L
  fdddd1d2c ckpt 4690: MK12: research index rebuilt and LEVERS.md cites the new study; all 10 fast
  7172be49e ckpt 4689: MK12c: league-aware market classes with pooled fallback, probe tests harden
  c552b4e9b ckpt 4688: MK12b: same-game copula (RfqCorrelation: Gauss+SameGame, measured rho by le
  42f706e6b ckpt 4687: MK12a: fixed last-look price tolerance (Novig echoes 0.4495 as 0.450; the o
  e742cad0f ckpt 4686: MK10k: rfq maker SHADOW (paper) per Tj
  b7ef6b620 ckpt 4685: TR7 logged: ledger drop = parlay collateral
  c1e50755e ckpt 4684: MK10j: open collateral cap 80 (wallet cash 12.5)
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
