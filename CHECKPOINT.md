# CHECKPOINT 4697 — read me first, then TASKS.md

**Written:** 2026-11-19T17:19:41Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `36e09e625` (this checkpoint is the commit after it)

## Just done
MK12 round 2 complete and deployed in SHADOW: last-look fix, SameGame copula, RfqProbe+RfqMarketModel (probe on, marketPricing OFF), prop pairs from market ratio (SHADOW on), fairErrorRel floor, min-support, rfq-market.yml, rfq_market.py; job 17:10 UTC runs all of it (priceable 100 -> 1290 per 5 min, probe 0.6/s, 0 failures); Tj told via Messages

## Do this next
about 2026-11-20 17:00 UTC: read Release rfq-market SUMMARY.md (rfq-market.yml) / python3 -I tools/rfq_market.py run <journals>: per-class demand, rule vs model on executed trades; if the evidence rule (research/novig_parlay_maker_2026-11-19.md s8) holds set marketPricing=true (never LIVE without Tj's money); read via=market ratio rows before propPairsFromMarket goes near LIVE; first settled live parlays (13 filled) still pending

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  36e09e625 ckpt 4696: MK11: ladder live in shadow; engine outage 16:59-17:10 repaired (dispatch),
  076fb99a1 ckpt 4695: MK12h: rfq-market.yml retries refused calls (the workflow token's 1,000 an 
  44115cb5c ckpt 4694: MK12g: RfqMarketModel.MIN_SUPPORT (5 answers above a candidate margin), wal
  7088a7870 ckpt 4693: MK12f: model floor fairErrorRel (fair x 2% x sqrt(legs)), xs band, via-quot
  12977b6eb ckpt 4692: MK12e: SHADOW capital fix, propPairsFromMarket (OFF), Parlays-view probe/mo
  6d511ca17 ckpt 4691: MK11: RfqLadder prop rung interpolation wired into the rfq leg fairs
  c4ae5ea25 ckpt 4691: MK12d: SHADOW judges RFQs as if the wallet were free (capital cap only in L
  fdddd1d2c ckpt 4690: MK12: research index rebuilt and LEVERS.md cites the new study; all 10 fast
  7172be49e ckpt 4689: MK12c: league-aware market classes with pooled fallback, probe tests harden
  c552b4e9b ckpt 4688: MK12b: same-game copula (RfqCorrelation: Gauss+SameGame, measured rho by le
```
