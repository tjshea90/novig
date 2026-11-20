# CHECKPOINT 4761 — read me first, then TASKS.md

**Written:** 2026-11-20T04:30:07Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `b4e9ef905` (this checkpoint is the commit after it)

## Just done
MK33b: rfq_pnl_study tool + daily workflow + research note: maker P&L at the clearing price by segment (first read: favourite-heavy parlays -20.6%, longshot-leg parlays +2.1%)

## Do this next
land, dispatch rfq-pnl-study.yml once to verify, then keep the live maker monitored: send_later chain every 30 min (fills, won/lost, P&L, halts), re-run the study daily and tune segments on it

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M research/review/CHANGES.md

## Last ten checkpoints
```
  c63a3bb19 ckpt 4760: MK34: maxLegFair .5 after 1 win 15 losses (-58.60)
  380c09355 ckpt 4760: MK32: settled maker parlays show adverse selection (31 of 32 legs won vs 23
  03264fefb ckpt 4759: MK31d: live legs up to 99% (liveMaxLegFair): 1,500 of 1,938 priced in-play 
  c8413ef4f ckpt 4758: MK10 03:52: 1 win 8 losses, -25.30; calibration job running, 95 quotes 0 wo
  e49cc94e0 ckpt 4757: MK33: leg calibration from 49k resolved outcomes; whole wallet to parlays
  b7809c7e5 ckpt 4754: MK31c: live in-play parlays ON (premium floors removed on shadow evidence: 
  81a2287de ckpt 4756: MK32: engine bids off + auto-bet paper: parlays get the wallet (Tj)
  f34f54531 ckpt 4755: MK10 03:22: first maker win (+9.55), realized -16.68
  b1a8863fd ckpt 4754: MK29d: tennis legs with no score source say so instead of waiting
  683bd866e ckpt 4753: MK31b: live fair watches only moneylines + the markets RFQs asked about (33
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
