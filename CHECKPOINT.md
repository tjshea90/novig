# CHECKPOINT 3344 — read me first, then TASKS.md

**Written:** 2026-10-15T06:12:33Z · **tests:** all 5 fast checks green
**Branch:** `claude/new-session-2gmle1` · **builds on:** `95db7ce91` (this checkpoint is the commit after it)

## Just done
TCL20d paper trading: CloudTrader tested (15 cases), engine hook + heartbeat fields (trade*), CloudMain wiring (ledger carried in trades.json, window-end cancel hook); live port not built yet

## Do this next
TCL20d live: CloudOrderPort over NovigOrderSocket with the trade key (read ApiBetPlacer/NovigOrderSocket first); then phone Settings control for off/paper/live + Kelly caps, workflow env, docs, tick TASKS

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  d0fb5137a ckpt 3343: TCL20d: CloudKelly (tested), CloudTrader+ledger+order port written, gates c
  c0e26d2e1 ckpt 3342: Took the lease by force (Tj: other session archived); merged origin/main wi
  ae727c03a ckpt 3341: TCL20c done: trading key swap+share (CloudTradeKey, SharedTradeVault, Cloud
  5f430adbd ckpt 3340: TCL20b done: EngineHours (08-22 ET, DST), End.WINDOW, workflow gate, Settin
  8e321e57c ckpt 3340: Third account (acct-7542bf) joined rotation: slot 2 19:20 ET, routine trig_
  85e368cd6 ckpt 3339: TCL20 step 1: scheduled task trig_011QGvat6eD9ER3bfE59T51T re-pointed (Vigi
  3ed534f1b ckpt 3339: Joined rotation as acct-27a37e slot 1 (13:20 ET), created routine trig_01Js
  fa85deab1 ckpt 3338: TCL20a done in the repo; Tj committed the Bash/create_trigger/update_trigge
  2a10e669b ckpt 3337: TCL20 written (Tj's words): autonomous bet+bid app on GitHub, 8am-10pm ET, 
  51950792c ckpt 3336: TCL19b: a refused runner address ends the engine job early and the workflow
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
