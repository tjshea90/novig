# CHECKPOINT 3681 — read me first, then TASKS.md

**Written:** 2026-10-18T22:19:27Z · **tests:** all 5 fast checks green
**Branch:** `ccr-7f278c49-1o357z` · **builds on:** `0ddec6571` (this checkpoint is the commit after it)

## Just done
TCL122-8 meter bars (no M3 stop dot, spent=full red), TCL122-3 CloudLink.refreshNow true refresh (Engine, Tracker), TCL122-2 faster on-screen polls, TCL122-7 Engine tab sections Overview/Money/APIs/Trends with no bid list

## Do this next
compile app (tools/test.sh :app:testDebugUnitTest --tests '*DashboardUiTest'), then Bids/Messages/Settings-APIs pull-to-refresh, then Live dashboard TCL122-4

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  4940b3a8f ckpt 3680: TCL122-0 toolbox audit written; took lease by Tj's force; merged mirage's T
  a7343bbf8 ckpt 3679: TCL122-1 Messages show only alerts, recommendations, pushed info; no bets/b
  9e6c0d604 ckpt 3678: TCL122 v1.0 big update written into TASKS
  da76e7451 ckpt 3677: v0.117.6 released, engine sockets verified live
  33792d2a4 ckpt 3676: pre-release: v0.117.6: the Live dashboard shows the GitHub engine's own soc
  9037bd41f ckpt 3675: engine publishes socket lines; dashboard shows them; v0.117.6 bump
  3298f164d ckpt 3674: v0.117.5 released and recorded
  25049b52b ckpt 3673: pre-release: v0.117.5: Live tab dashboard: every socket's state and last pu
  cecb00cfb ckpt 3672: Live dashboard card, v0.117.5 bump
  6e13a48a1 ckpt 3671: v0.117.4 released+recorded, engine restarted
```

(9 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
