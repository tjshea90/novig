# CHECKPOINT 4822 — read me first, then TASKS.md

**Written:** 2026-11-21T06:57:01Z · **tests:** all 11 fast checks green
**Branch:** `ccr-bc2a800c-fgp2ck` · **builds on:** `de2826048` (this checkpoint is the commit after it)

## Just done
UW1: rfq_set reserveByWinRate=true walletLegShare=.5 dayLossStopPct=.5 cashReserve=.5 for the $54 wallet (the .25 leg cap = $13.5 refused every parlay under 43% win chance; the 30% stop = $16 trips on one loss); Novig still in maintenance, takes effect on reopening

## Do this next
CS1c follow-up ~07:07 UTC checks the reopen; then verify the maker quotes the 25-43% band and quote wins rise; LP1 re-judge and ON2 monitor queued; revert line in CHANGES UW1

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M research/review/CHANGES.md

## Last ten checkpoints
```
  f8f4896b7 ckpt 4821: CS1b: Novig maintenance (423 SYSTEM_LOCKED 'Trading is paused: Maintenance'
  9b9f9b7d0 ckpt 4820: CS1b: Novig answered MAKER_BARRED to the maker at 06:37:06 UTC (confirm rat
  83979cb13 ckpt 4819: LP1b: rfq-pnl-study.yml fixed (numpy venv, calibration selftest inside the 
  a91955bfe ckpt 4818: CS1: win-rate-weighted quote collateral hold (RfqConfig.reserveByWinRate, R
  6e2182c81 ckpt 4817: ON2 monitor check-in #3 (06:07 UTC): healthy; maker connected, not halted, 
  016b64744 ckpt 4816: LP1: in-play parlays LIVE (liveParlays LIVE, per-leg cap .8, ~3 pt margin) 
  5aa2d1d57 ckpt 4815: Handoff saved (.handoff/handoff.md + archive 2026-11-21T06-00); CLAUDE.md/A
  0091deef3 ckpt 4814: FV2: rfq profile widened on graded evidence (minFair .35->.25: .25-.35 band
  50c1e2292 ckpt 4813: BL1: delayed syncNow on resume (blurry return), compile checked
  11ef34574 ckpt 4813: FV1 analysis: research/parlay_fair_coverage_2026-11-21.md (weighted attribu
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
