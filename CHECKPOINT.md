# CHECKPOINT 4751 — read me first, then TASKS.md

**Written:** 2026-11-20T02:55:06Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `0d45e2bde` (this checkpoint is the commit after it)

## Just done
MK31: live in-play parlay pricing from Novig's live book (LiveFair), shadow by default, tests + full suite green

## Do this next
land, ONE graceful engine reset (board note first), read journals 20-30 min later: in-play shadow rows vs market executed price, then liveParlays=LIVE with the evidence-based premium

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M research/review/CHANGES.md

## Last ten checkpoints
```
  3f0ba0603 ckpt 4750: MK29b: grade finished legs without a market id from final scores (legsToSco
  9fa189501 ckpt 4749: MK30: 0 of 203 quotes' RFQs ever traded; 95% of evening trades are in-play 
  2ec9b5352 ckpt 4748: MK29b: price floors lowered to the market's level (margin .006, minPoints .
  da0f9fad9 ckpt 4747: pre-release: MK29: parlay legs graded from final scores; in-play legs blue 
  474452154 ckpt 4746: pre-ship: MK29: parlay legs graded from final scores; in-play legs blue not
  68d5bcadc ckpt 4745: MK29: grade parlay legs from final scores (ParlayLegScores) so finished leg
  805dc24a3 ckpt 4744: MK28: quote markup was 3.9 pts over fair vs the market's +0.3: liveMinRetur
  6ab5b5f5c ckpt 4743: MK27: full suite green, protected mapping lands
  47991ec5d ckpt 4742: MK28b: correction: journal under-samples no-fair declines; live legs and ca
  fd84d3160 ckpt 4741: MK28: liveMinReturn .008 (rfq_set, stake cap unchanged)
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
