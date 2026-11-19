# CHECKPOINT 4691 — read me first, then TASKS.md

**Written:** 2026-11-19T16:37:54Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `e313abc8c` (this checkpoint is the commit after it)

## Just done
MK12d: SHADOW judges RFQs as if the wallet were free (capital cap only in LIVE), tools/rfq_market.py (probe demand table + rule-vs-model on executed trades) + tools/test_rfq.sh fast check, NOVIG_API.md 33.2; engine reset onto main done: new job 16:36 UTC has probe + copula (sameGamePremium 0.02)

## Do this next
after the new job warms (~10 min cold scan): read status.rfq.probe/market, download the next journal and run rfq_market.py; land the SHADOW capital fix with the next deploy

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M tools/rfq_market.py
    ?? tools/test_rfq.sh

## Last ten checkpoints
```
  fdddd1d2c ckpt 4690: MK12: research index rebuilt and LEVERS.md cites the new study; all 10 fast
  7172be49e ckpt 4689: MK12c: league-aware market classes with pooled fallback, probe tests harden
  c552b4e9b ckpt 4688: MK12b: same-game copula (RfqCorrelation: Gauss+SameGame, measured rho by le
  42f706e6b ckpt 4687: MK12a: fixed last-look price tolerance (Novig echoes 0.4495 as 0.450; the o
  e742cad0f ckpt 4686: MK10k: rfq maker SHADOW (paper) per Tj
  b7ef6b620 ckpt 4685: TR7 logged: ledger drop = parlay collateral
  c1e50755e ckpt 4684: MK10j: open collateral cap 80 (wallet cash 12.5)
  5eff872a6 ckpt 4683: WATCH 11-19 13:20 slot: engine healthy, parlay check no change
  082eeaabf ckpt 4682: MK10 11:08: fills starting (9 won, 8 filled)
  8624ed34e ckpt 4681: MK10i: rfq floor 0.7pt (minPoints .007 margin .015 minReturn .004)
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
