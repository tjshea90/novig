# CHECKPOINT 3682 — read me first, then TASKS.md

**Written:** 2026-10-18T22:25:54Z · **tests:** all 5 fast checks green
**Branch:** `ccr-7f278c49-1o357z` · **builds on:** `35d9c53e2` (this checkpoint is the commit after it)

## Just done
TCL122-6 SlateProfit model (slate days, fixed start) + new profit card (Today/Yesterday line, 7/30/All bars, day list); MoneyIn card removed; TCL122-1 engine stops posting bet/bid messages; pull-to-refresh on Bids, Messages, Settings APIs/usage

## Do this next
wait for /tmp/claude-0/t3.log tests (SlateProfitTest, ProfitChartTest...), fix, then TCL122-4 Live dashboard GitHub/Phone switch

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  049c471bf ckpt 3681: TCL122-8 meter bars (no M3 stop dot, spent=full red), TCL122-3 CloudLink.re
  4940b3a8f ckpt 3680: TCL122-0 toolbox audit written; took lease by Tj's force; merged mirage's T
  a7343bbf8 ckpt 3679: TCL122-1 Messages show only alerts, recommendations, pushed info; no bets/b
  9e6c0d604 ckpt 3678: TCL122 v1.0 big update written into TASKS
  da76e7451 ckpt 3677: v0.117.6 released, engine sockets verified live
  33792d2a4 ckpt 3676: pre-release: v0.117.6: the Live dashboard shows the GitHub engine's own soc
  9037bd41f ckpt 3675: engine publishes socket lines; dashboard shows them; v0.117.6 bump
  3298f164d ckpt 3674: v0.117.5 released and recorded
  25049b52b ckpt 3673: pre-release: v0.117.5: Live tab dashboard: every socket's state and last pu
  cecb00cfb ckpt 3672: Live dashboard card, v0.117.5 bump
```

(10 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
