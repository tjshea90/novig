# CHECKPOINT 4687 — read me first, then TASKS.md

**Written:** 2026-11-19T16:05:33Z · **tests:** all 10 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `c7be86b23` (this checkpoint is the commit after it)

## Just done
MK12a: fixed last-look price tolerance (Novig echoes 0.4495 as 0.450; the old 5e-4 bound rejected our own win and caused MAKER_LOCKED); RfqMakerTest covers both rounding extremes. Research so far: Novig keyless /rfq/v2/quote/unauthenticated = real 3 s auction (usable as a market-price probe), journals 11-18/11-19 downloaded to scratchpad

## Do this next
MK12b: analyse journals by class (volume, gap, same-game implied correlation), design RfqProbe + fitted competitor-price model + Novig-mid fallback fair + ladder + copula; then land and reset engine

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  e742cad0f ckpt 4686: MK10k: rfq maker SHADOW (paper) per Tj
  b7ef6b620 ckpt 4685: TR7 logged: ledger drop = parlay collateral
  c1e50755e ckpt 4684: MK10j: open collateral cap 80 (wallet cash 12.5)
  5eff872a6 ckpt 4683: WATCH 11-19 13:20 slot: engine healthy, parlay check no change
  082eeaabf ckpt 4682: MK10 11:08: fills starting (9 won, 8 filled)
  8624ed34e ckpt 4681: MK10i: rfq floor 0.7pt (minPoints .007 margin .015 minReturn .004)
  87e289ffe ckpt 4680: MK10 08:37 check: no change, evidence logged
  2da22662c ckpt 4679: MK10h: rfq minReturn 0.006
  135bf46a5 ckpt 4678: MK10g: rfq minReturn 0.01
  8c27468c7 ckpt 4677: MK10f: rfq maxLegs 7 minFair 0.01 (long all-priced parlays)
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
