# CHECKPOINT 4740 — read me first, then TASKS.md

**Written:** 2026-11-20T01:55:25Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `32f0e0778` (this checkpoint is the commit after it)

## Just done
MK27: longshotsOnly off (Tj's filter), protected markets use the unprotected ladder; trade-level analysis logged

## Do this next
full suite then land the protected mapping; deploy with the next reset; same-game correlation shadow pricing (market pays +4.1 pts over independent for those trades); more cash is the real limit

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M research/review/CHANGES.md

## Last ten checkpoints
```
  0934ea2dc ckpt 4739: MK21 evidence check: extrapolated legs mostly in same-game combos (none quo
  9c900f1d9 ckpt 4738: MK26: exact replay: engine quotes what Tj's caps allow (25% of the morning'
  12d05ce7e ckpt 4738: MK27: minLead 1 min, propPairsLive per Tj; marketPricing held for his yes
  b498e37bf ckpt 4737: MK22-MK25 done and deployed (job 44653302569): prop votes fair, ladder extr
  7bc79a19c ckpt 4736: MK26: warm-job decline breakdown; minLeadMs 10 min
  d4dc7a7d7 ckpt 4735: MK25: rfq_set maxLegs guard 10 -> 100; maxLegs=100 written
  5e7d3ae94 ckpt 4734: MK25: no limit on prop legs (maxPropLegs) and total legs; full suite green
  ac4cb4da1 ckpt 4733: MK24: 3-6 prop-leg parlays priced at the plain product in shadow (propLegsS
  e95787339 ckpt 4732: MK14 follow-up check ok; ladder legs now carry name/desc/market id for hub 
  1fc4eb7f0 ckpt 4731: MK23: live-morning vs today's rules replay: the drop is the win-chance filt
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
