# CHECKPOINT 4694 — read me first, then TASKS.md

**Written:** 2026-11-19T16:58:45Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `c47730626` (this checkpoint is the commit after it)

## Just done
MK12g: RfqMarketModel.MIN_SUPPORT (5 answers above a candidate margin), walk-forward validation table (fit 60% / test 40% per league: held in 4 of 5 leagues, flat floor nets 0), data tests green (3103)

## Do this next
deploy: engine_set --reset onto main (batch 2+3: SHADOW capital fix, fairErrorRel, xs band, propPairsFromMarket); then set propPairsFromMarket=true in shadow; read first probe table; Messages note to Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  7088a7870 ckpt 4693: MK12f: model floor fairErrorRel (fair x 2% x sqrt(legs)), xs band, via-quot
  12977b6eb ckpt 4692: MK12e: SHADOW capital fix, propPairsFromMarket (OFF), Parlays-view probe/mo
  6d511ca17 ckpt 4691: MK11: RfqLadder prop rung interpolation wired into the rfq leg fairs
  c4ae5ea25 ckpt 4691: MK12d: SHADOW judges RFQs as if the wallet were free (capital cap only in L
  fdddd1d2c ckpt 4690: MK12: research index rebuilt and LEVERS.md cites the new study; all 10 fast
  7172be49e ckpt 4689: MK12c: league-aware market classes with pooled fallback, probe tests harden
  c552b4e9b ckpt 4688: MK12b: same-game copula (RfqCorrelation: Gauss+SameGame, measured rho by le
  42f706e6b ckpt 4687: MK12a: fixed last-look price tolerance (Novig echoes 0.4495 as 0.450; the o
  e742cad0f ckpt 4686: MK10k: rfq maker SHADOW (paper) per Tj
  b7ef6b620 ckpt 4685: TR7 logged: ledger drop = parlay collateral
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
