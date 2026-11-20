# CHECKPOINT 4744 — read me first, then TASKS.md

**Written:** 2026-11-20T02:04:51Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `e3bf13421` (this checkpoint is the commit after it)

## Just done
MK28: quote markup was 3.9 pts over fair vs the market's +0.3: liveMinReturn back to 0.004

## Do this next
watch quotes won/filled over the next hour; if still none, lower the safe-edge floor (fairErrorRel) toward 0.01 and read QUOTE_SENT vs EXECUTED; protected mapping deploys at next reset; same-game correlation shadow

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M research/review/CHANGES.md

## Last ten checkpoints
```
  6ab5b5f5c ckpt 4743: MK27: full suite green, protected mapping lands
  47991ec5d ckpt 4742: MK28b: correction: journal under-samples no-fair declines; live legs and ca
  fd84d3160 ckpt 4741: MK28: liveMinReturn .008 (rfq_set, stake cap unchanged)
  092b7c49b ckpt 4740: MK28: live floor .03 -> .008, stake cap 6 (Tj wants fills)
  1240d5302 ckpt 4740: MK27: longshotsOnly off (Tj's filter), protected markets use the unprotecte
  0934ea2dc ckpt 4739: MK21 evidence check: extrapolated legs mostly in same-game combos (none quo
  9c900f1d9 ckpt 4738: MK26: exact replay: engine quotes what Tj's caps allow (25% of the morning'
  12d05ce7e ckpt 4738: MK27: minLead 1 min, propPairsLive per Tj; marketPricing held for his yes
  b498e37bf ckpt 4737: MK22-MK25 done and deployed (job 44653302569): prop votes fair, ladder extr
  7bc79a19c ckpt 4736: MK26: warm-job decline breakdown; minLeadMs 10 min
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
