# CHECKPOINT 3347 — read me first, then TASKS.md

**Written:** 2026-10-15T06:34:28Z · **tests:** all 5 fast checks green
**Branch:** `claude/new-session-2gmle1` · **builds on:** `3e59b0dea` (this checkpoint is the commit after it)

## Just done
TCL20e: engine scan study segments + engine-grade.yml grader + GradeMain + TradeSettle + engine-results branch applied by the engine + digest sections (study + trade_summary); EngineStudyTest/TradeSettleTest/selftests green; CLAUDE.md + TASKS ticked

## Do this next
run the whole test floor (bash tools/test.sh) in the background, then TCL20f (rotation routines: verify all three exist), then ship a release (ship.sh) so the phone gets Engine trading

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  2469c3107 ckpt 3346: TCL20d ticked: engine trading (paper/live, Kelly, port, UI, phone auto-bet 
  633957eee ckpt 3345: TCL20d: CloudNovigPort (IOC by REST, lost-answer lookup, stop on location/k
  81d9bcf12 ckpt 3344: TCL20d paper trading: CloudTrader tested (15 cases), engine hook + heartbea
  d0fb5137a ckpt 3343: TCL20d: CloudKelly (tested), CloudTrader+ledger+order port written, gates c
  c0e26d2e1 ckpt 3342: Took the lease by force (Tj: other session archived); merged origin/main wi
  ae727c03a ckpt 3341: TCL20c done: trading key swap+share (CloudTradeKey, SharedTradeVault, Cloud
  5f430adbd ckpt 3340: TCL20b done: EngineHours (08-22 ET, DST), End.WINDOW, workflow gate, Settin
  8e321e57c ckpt 3340: Third account (acct-7542bf) joined rotation: slot 2 19:20 ET, routine trig_
  85e368cd6 ckpt 3339: TCL20 step 1: scheduled task trig_011QGvat6eD9ER3bfE59T51T re-pointed (Vigi
  3ed534f1b ckpt 3339: Joined rotation as acct-27a37e slot 1 (13:20 ET), created routine trig_01Js
```

(13 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
