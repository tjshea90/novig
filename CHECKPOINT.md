# CHECKPOINT 4827 — read me first, then TASKS.md

**Written:** 2026-11-21T09:17:56Z · **tests:** all 11 fast checks green
**Branch:** `ccr-bc2a800c-fgp2ck` · **builds on:** `26d29c7ab` (this checkpoint is the commit after it)

## Just done
UW3 monitor #2 (09:16 UTC): maker live and quoting ~11/min, 226 quotes this run, 0 won yet (expected 0.5-1.6), no halt; cash-short gone; 3-leg stays excluded (-8.4% graded); runs replaced by a tjshea90-token dispatch at 08:22 and 08:58 (harmless)

## Do this next
monitor #3 send_later 60 min (to ~16:00 UTC): first wins/fills in the US-daytime slate, cash vs collateral, any halt; coverage levers FV1b/FV1c only if fills stay at 0 through the daytime slate

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M research/review/CHANGES.md

## Last ten checkpoints
```
  26d29c7ab ckpt 4826: UW3: quoteWinFloor .01 live; new run 44884601470, 44 quotes in 4 min, cash-
  9bc7774e6 ckpt 4825: UW3: Novig reopened ~08:13 UTC (1h36 maintenance), maker connected, 72 quot
  5b9306898 ckpt 4824: UW2: parlay loss stop OFF (dayLossStopPct 0), auto-bet daily stop 1.0, scar
  60b9ab30a ckpt 4823: LP1 follow-up: study fixed and refreshed (16,205 graded, 827 in-play, 223 i
  66a26e6dd ckpt 4822: UW1: rfq_set reserveByWinRate=true walletLegShare=.5 dayLossStopPct=.5 cash
  f8f4896b7 ckpt 4821: CS1b: Novig maintenance (423 SYSTEM_LOCKED 'Trading is paused: Maintenance'
  9b9f9b7d0 ckpt 4820: CS1b: Novig answered MAKER_BARRED to the maker at 06:37:06 UTC (confirm rat
  83979cb13 ckpt 4819: LP1b: rfq-pnl-study.yml fixed (numpy venv, calibration selftest inside the 
  a91955bfe ckpt 4818: CS1: win-rate-weighted quote collateral hold (RfqConfig.reserveByWinRate, R
  6e2182c81 ckpt 4817: ON2 monitor check-in #3 (06:07 UTC): healthy; maker connected, not halted, 
```
