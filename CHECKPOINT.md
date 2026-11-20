# CHECKPOINT 4734 — read me first, then TASKS.md

**Written:** 2026-11-20T01:22:14Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `45db9d873` (this checkpoint is the commit after it)

## Just done
MK25: no limit on prop legs (maxPropLegs) and total legs; full suite green

## Do this next
set maxLegs=100 in rfq-maker.json, reset the engine once (confirm startedAtMs), watch priceable count and halts; app stepper 'No limit' in the next app release

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  ac4cb4da1 ckpt 4733: MK24: 3-6 prop-leg parlays priced at the plain product in shadow (propLegsS
  e95787339 ckpt 4732: MK14 follow-up check ok; ladder legs now carry name/desc/market id for hub 
  1fc4eb7f0 ckpt 4731: MK23: live-morning vs today's rules replay: the drop is the win-chance filt
  52afcfd55 ckpt 4730: MK23 refined: carry-over alone is not enough (fair age); partial-result pub
  1cf774173 ckpt 4729: MK23 logged: cold-start fair index explains 'engine priced more earlier'
  7fa234079 ckpt 4728: MK22: PropLadderFair: the maker's prop fair from every book's price at a st
  0b0247ecf ckpt 4727: MK22: propcov task (what PropLine really sends for player props: two-sided 
  51f0f638c ckpt 4726: ANALYST 2026-11-19: no change, watch note logged
  d8c6c2271 ckpt 4725: MK21: ladder extrapolation of unposted NBA/NHL/NFL prop rungs (RfqLadder.ex
  2a6be887b ckpt 4724: MK20: same-game prop-pair model (PropPairs, upper-bound ratios from PropLin
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
