# CHECKPOINT 4825 — read me first, then TASKS.md

**Written:** 2026-11-21T08:21:17Z · **tests:** all 11 fast checks green
**Branch:** `ccr-bc2a800c-fgp2ck` · **builds on:** `5b9306898` (this checkpoint is the commit after it)

## Just done
UW3: Novig reopened ~08:13 UTC (1h36 maintenance), maker connected, 72 quotes in 6 min but cash-short refused 658: quoteWinFloor .03 -> .01 (capacity ~11 -> ~33 quotes in flight at 99.5%; measured win rate 0.2-0.7%)

## Do this next
re-check in ~5 min: quotes/min up, cash-short down, first win/fill, any maker_insolvent halt (then quoteWinFloor back to .03 / reserveByWinRate=false); ON2 monitor re-arm 45 min

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M research/review/CHANGES.md

## Last ten checkpoints
```
  5b9306898 ckpt 4824: UW2: parlay loss stop OFF (dayLossStopPct 0), auto-bet daily stop 1.0, scar
  60b9ab30a ckpt 4823: LP1 follow-up: study fixed and refreshed (16,205 graded, 827 in-play, 223 i
  66a26e6dd ckpt 4822: UW1: rfq_set reserveByWinRate=true walletLegShare=.5 dayLossStopPct=.5 cash
  f8f4896b7 ckpt 4821: CS1b: Novig maintenance (423 SYSTEM_LOCKED 'Trading is paused: Maintenance'
  9b9f9b7d0 ckpt 4820: CS1b: Novig answered MAKER_BARRED to the maker at 06:37:06 UTC (confirm rat
  83979cb13 ckpt 4819: LP1b: rfq-pnl-study.yml fixed (numpy venv, calibration selftest inside the 
  a91955bfe ckpt 4818: CS1: win-rate-weighted quote collateral hold (RfqConfig.reserveByWinRate, R
  6e2182c81 ckpt 4817: ON2 monitor check-in #3 (06:07 UTC): healthy; maker connected, not halted, 
  016b64744 ckpt 4816: LP1: in-play parlays LIVE (liveParlays LIVE, per-leg cap .8, ~3 pt margin) 
  5aa2d1d57 ckpt 4815: Handoff saved (.handoff/handoff.md + archive 2026-11-21T06-00); CLAUDE.md/A
```
