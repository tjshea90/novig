# CHECKPOINT 4753 — read me first, then TASKS.md

**Written:** 2026-11-20T03:07:37Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `b86de7d2a` (this checkpoint is the commit after it)

## Just done
MK31b: live fair watches only moneylines + the markets RFQs asked about (330 max): the first deploy pinned 2,004 markets

## Do this next
land, ONE graceful reset, then rfq_set liveParlays=SHADOW, read in-play shadow rows vs market executed prices in 20-30 min, then LIVE with the evidence-based premium

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  58ffee2b3 ckpt 4752: MK29c: leg grading drops tennis round suffix and retries reversed home/away
  3fbfafb7b ckpt 4751: MK31: live in-play parlay pricing from Novig's live book (LiveFair), shadow
  3f0ba0603 ckpt 4750: MK29b: grade finished legs without a market id from final scores (legsToSco
  9fa189501 ckpt 4749: MK30: 0 of 203 quotes' RFQs ever traded; 95% of evening trades are in-play 
  2ec9b5352 ckpt 4748: MK29b: price floors lowered to the market's level (margin .006, minPoints .
  da0f9fad9 ckpt 4747: pre-release: MK29: parlay legs graded from final scores; in-play legs blue 
  474452154 ckpt 4746: pre-ship: MK29: parlay legs graded from final scores; in-play legs blue not
  68d5bcadc ckpt 4745: MK29: grade parlay legs from final scores (ParlayLegScores) so finished leg
  805dc24a3 ckpt 4744: MK28: quote markup was 3.9 pts over fair vs the market's +0.3: liveMinRetur
  6ab5b5f5c ckpt 4743: MK27: full suite green, protected mapping lands
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
