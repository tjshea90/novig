# CHECKPOINT 4829 — read me first, then TASKS.md

**Written:** 2026-11-21T11:24:59Z · **tests:** all 11 fast checks green
**Branch:** `ccr-bc2a800c-fgp2ck` · **builds on:** `0dc92999a` (this checkpoint is the commit after it)

## Just done
UW3 monitor #4 (11:22 UTC): Novig pause ended ~11:00, 1,872 quotes 0 won; funnel: 10 of 2,360 quotes executed and all 10 lost to a price 0.2-1.7 pts under ours (fair+.001): wins need quoting under our own fair = Tj's policy call, class B not met; engine bids filled 10 for $8.10 (cash 40.48)

## Do this next
monitor #5 send_later 60 min: US-daytime funnel re-run (QUOTE_SENT x EXECUTED by rfqId), first wins/fills, cash vs collateral, halts; tell Tj the below-fair option once; FV1b/FV1c only if fills stay 0 through ~16:00 UTC

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M research/review/CHANGES.md

## Last ten checkpoints
```
  0dc92999a ckpt 4828: UW3 monitor #3 (10:19 UTC): Novig exchange paused again (SYSTEM_LOCKED from
  697c57c61 ckpt 4827: UW3 monitor #2 (09:16 UTC): maker live and quoting ~11/min, 226 quotes this
  26d29c7ab ckpt 4826: UW3: quoteWinFloor .01 live; new run 44884601470, 44 quotes in 4 min, cash-
  9bc7774e6 ckpt 4825: UW3: Novig reopened ~08:13 UTC (1h36 maintenance), maker connected, 72 quot
  5b9306898 ckpt 4824: UW2: parlay loss stop OFF (dayLossStopPct 0), auto-bet daily stop 1.0, scar
  60b9ab30a ckpt 4823: LP1 follow-up: study fixed and refreshed (16,205 graded, 827 in-play, 223 i
  66a26e6dd ckpt 4822: UW1: rfq_set reserveByWinRate=true walletLegShare=.5 dayLossStopPct=.5 cash
  f8f4896b7 ckpt 4821: CS1b: Novig maintenance (423 SYSTEM_LOCKED 'Trading is paused: Maintenance'
  9b9f9b7d0 ckpt 4820: CS1b: Novig answered MAKER_BARRED to the maker at 06:37:06 UTC (confirm rat
  83979cb13 ckpt 4819: LP1b: rfq-pnl-study.yml fixed (numpy venv, calibration selftest inside the 
```
