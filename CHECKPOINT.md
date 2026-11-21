# CHECKPOINT 4831 — read me first, then TASKS.md

**Written:** 2026-11-21T12:33:34Z · **tests:** all 11 fast checks green
**Branch:** `ccr-bc2a800c-fgp2ck` · **builds on:** `f8fac4f58` (this checkpoint is the commit after it)

## Just done
UW4: RfqConfig.quoteBelowFairPoints (default 0 = off; pregame quote = fair minus N points, rounded up; rfq_set limit 0-0.03; RfqBelowFairTest); funnel evidence 21 of 21 contested RFQs lost to a price 0.2-2.1 pts under ours; full floor 4553 passed

## Do this next
land on main -> engine_set.py --reset -> confirm new run loaded the config -> rfq_set quoteBelowFairPoints=0.006 -> CHANGES.md UW4 (stop rule: first 12 settled fills <= -10% on collateral, -$12 net on parlay fills, or any maker_insolvent halt) -> monitor #5 fires 12:25 (already past): fetch status

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  d6dee9cdc ckpt 4830: ENGINEER 11-21 13:20 slot: watch only, engine healthy, lane owned by UW3
  27dc66266 ckpt 4829: UW3 monitor #4 (11:22 UTC): Novig pause ended ~11:00, 1,872 quotes 0 won; f
  0dc92999a ckpt 4828: UW3 monitor #3 (10:19 UTC): Novig exchange paused again (SYSTEM_LOCKED from
  697c57c61 ckpt 4827: UW3 monitor #2 (09:16 UTC): maker live and quoting ~11/min, 226 quotes this
  26d29c7ab ckpt 4826: UW3: quoteWinFloor .01 live; new run 44884601470, 44 quotes in 4 min, cash-
  9bc7774e6 ckpt 4825: UW3: Novig reopened ~08:13 UTC (1h36 maintenance), maker connected, 72 quot
  5b9306898 ckpt 4824: UW2: parlay loss stop OFF (dayLossStopPct 0), auto-bet daily stop 1.0, scar
  60b9ab30a ckpt 4823: LP1 follow-up: study fixed and refreshed (16,205 graded, 827 in-play, 223 i
  66a26e6dd ckpt 4822: UW1: rfq_set reserveByWinRate=true walletLegShare=.5 dayLossStopPct=.5 cash
  f8f4896b7 ckpt 4821: CS1b: Novig maintenance (423 SYSTEM_LOCKED 'Trading is paused: Maintenance'
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
