# CHECKPOINT 4748 — read me first, then TASKS.md

**Written:** 2026-11-20T02:36:27Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `460b5b64a` (this checkpoint is the commit after it)

## Just done
MK29b: price floors lowered to the market's level (margin .006, minPoints .002, fairErrorRel .006); fill expectation logged

## Do this next
check fills at 02:46 (scheduled); read QUOTE_SENT vs EXECUTED of the warm job; same-game correlation shadow; mirage carries fairKept job to job (do not touch CloudMain fairKept)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M research/review/CHANGES.md

## Last ten checkpoints
```
  da0f9fad9 ckpt 4747: pre-release: MK29: parlay legs graded from final scores; in-play legs blue 
  474452154 ckpt 4746: pre-ship: MK29: parlay legs graded from final scores; in-play legs blue not
  68d5bcadc ckpt 4745: MK29: grade parlay legs from final scores (ParlayLegScores) so finished leg
  805dc24a3 ckpt 4744: MK28: quote markup was 3.9 pts over fair vs the market's +0.3: liveMinRetur
  6ab5b5f5c ckpt 4743: MK27: full suite green, protected mapping lands
  47991ec5d ckpt 4742: MK28b: correction: journal under-samples no-fair declines; live legs and ca
  fd84d3160 ckpt 4741: MK28: liveMinReturn .008 (rfq_set, stake cap unchanged)
  092b7c49b ckpt 4740: MK28: live floor .03 -> .008, stake cap 6 (Tj wants fills)
  1240d5302 ckpt 4740: MK27: longshotsOnly off (Tj's filter), protected markets use the unprotecte
  0934ea2dc ckpt 4739: MK21 evidence check: extrapolated legs mostly in same-game combos (none quo
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
