# CHECKPOINT 4692 — read me first, then TASKS.md

**Written:** 2026-11-19T16:45:46Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `87d2b4878` (this checkpoint is the commit after it)

## Just done
MK12e: SHADOW capital fix, propPairsFromMarket (OFF), Parlays-view probe/model tiles, rfq_set fields, LIVE runbook + outside context in the research doc; FULL suite green (4421 tests)

## Do this next
relative-error floor for the model path (fairErrorRel), xs band (<3%), exclude via-quotes from evidence counters, then land and redeploy once the first hour of probe data is read

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6d511ca17 ckpt 4691: MK11: RfqLadder prop rung interpolation wired into the rfq leg fairs
  c4ae5ea25 ckpt 4691: MK12d: SHADOW judges RFQs as if the wallet were free (capital cap only in L
  fdddd1d2c ckpt 4690: MK12: research index rebuilt and LEVERS.md cites the new study; all 10 fast
  7172be49e ckpt 4689: MK12c: league-aware market classes with pooled fallback, probe tests harden
  c552b4e9b ckpt 4688: MK12b: same-game copula (RfqCorrelation: Gauss+SameGame, measured rho by le
  42f706e6b ckpt 4687: MK12a: fixed last-look price tolerance (Novig echoes 0.4495 as 0.450; the o
  e742cad0f ckpt 4686: MK10k: rfq maker SHADOW (paper) per Tj
  b7ef6b620 ckpt 4685: TR7 logged: ledger drop = parlay collateral
  c1e50755e ckpt 4684: MK10j: open collateral cap 80 (wallet cash 12.5)
  5eff872a6 ckpt 4683: WATCH 11-19 13:20 slot: engine healthy, parlay check no change
```

(10 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
