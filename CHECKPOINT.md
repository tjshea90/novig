# CHECKPOINT 3685 — read me first, then TASKS.md

**Written:** 2026-10-18T22:45:16Z · **tests:** all 5 fast checks green
**Branch:** `ccr-7f278c49-1o357z` · **builds on:** `43a033366` (this checkpoint is the commit after it)

## Just done
TCL122-5 SX socket freshness fix + API audit doc; TCL122-9 settings regroup (engine first, rows renamed, CNO dup row gone, deposit field gone, live switches moved to Live tab, phone scan hidden when cloud drives); TCL122-11 found+fixed: engine trader got raw kinds (never auto-bet totals/team totals) -> CloudEngine.forTrading

## Do this next
read /tmp/claude-0/t7.log; then CHANGES.md entry for forTrading; TCL122-10 UI/code audit; TCL122-12 other fixes; full tests; v1.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/src/main/kotlin/com/tjshea/vigilant/app/ui/LiveScreen.kt
     M app/src/test/kotlin/com/tjshea/vigilant/app/ClaudeScreenUiTest.kt

## Last ten checkpoints
```
  7f8a89ad4 ckpt 3684: TCL122-4 phone half written: LiveBoard model, LightUi phone sockets/stats/t
  e8159d38e ckpt 3683: TCL122-4 engine half: liveOnGitHub/phoneSockets/phoneLive predicates; forCl
  fc623715e ckpt 3682: TCL122-6 SlateProfit model (slate days, fixed start) + new profit card (Tod
  049c471bf ckpt 3681: TCL122-8 meter bars (no M3 stop dot, spent=full red), TCL122-3 CloudLink.re
  4940b3a8f ckpt 3680: TCL122-0 toolbox audit written; took lease by Tj's force; merged mirage's T
  a7343bbf8 ckpt 3679: TCL122-1 Messages show only alerts, recommendations, pushed info; no bets/b
  9e6c0d604 ckpt 3678: TCL122 v1.0 big update written into TASKS
  da76e7451 ckpt 3677: v0.117.6 released, engine sockets verified live
  33792d2a4 ckpt 3676: pre-release: v0.117.6: the Live dashboard shows the GitHub engine's own soc
  9037bd41f ckpt 3675: engine publishes socket lines; dashboard shows them; v0.117.6 bump
```

(9 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
