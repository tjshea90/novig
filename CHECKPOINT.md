# CHECKPOINT 4737 — read me first, then TASKS.md

**Written:** 2026-11-20T01:47:31Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `7bc79a19c` (this checkpoint is the commit after it)

## Just done
MK22-MK25 done and deployed (job 44653302569): prop votes fair, ladder extrapolation, pair model shadow, prop-leg shadow, no limit on legs; evening RFQ flow is 66% live-game parlays

## Do this next
build same-game correlation pricing in SHADOW (game line + prop of one game, 3+ legs of one game): 41% of pregame RFQs; read journal rows 'N prop legs ... shadow' and 'ladder-votes' legs vs market EXECUTED prices; Tj decision pending on win-chance limit; app stepper 'No limit' in next app release; MK21 check fires 01:42 UTC

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  7bc79a19c ckpt 4736: MK26: warm-job decline breakdown; minLeadMs 10 min
  d4dc7a7d7 ckpt 4735: MK25: rfq_set maxLegs guard 10 -> 100; maxLegs=100 written
  5e7d3ae94 ckpt 4734: MK25: no limit on prop legs (maxPropLegs) and total legs; full suite green
  ac4cb4da1 ckpt 4733: MK24: 3-6 prop-leg parlays priced at the plain product in shadow (propLegsS
  e95787339 ckpt 4732: MK14 follow-up check ok; ladder legs now carry name/desc/market id for hub 
  1fc4eb7f0 ckpt 4731: MK23: live-morning vs today's rules replay: the drop is the win-chance filt
  52afcfd55 ckpt 4730: MK23 refined: carry-over alone is not enough (fair age); partial-result pub
  1cf774173 ckpt 4729: MK23 logged: cold-start fair index explains 'engine priced more earlier'
  7fa234079 ckpt 4728: MK22: PropLadderFair: the maker's prop fair from every book's price at a st
  0b0247ecf ckpt 4727: MK22: propcov task (what PropLine really sends for player props: two-sided 
```
