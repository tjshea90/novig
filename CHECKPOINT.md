# CHECKPOINT 3351 — read me first, then TASKS.md

**Written:** 2026-10-15T14:24:54Z · **tests:** all 5 fast checks green
**Branch:** `ccr-5182c4e7-h37dlu` · **builds on:** `7ddddc157` (this checkpoint is the commit after it)

## Just done
engine empty-wallet sleep (CloudEngine.walletProbe, status.walletSleep, Settings line), engine tests green

## Do this next
compile :cloud and :app, then phone pause/resume + sync-on-open verification, key advisor

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  3a82a6ef0 ckpt 3350: rotation: missed-slot detector + checkin/cover + routine repo step, tested
  17bc01bf7 ckpt 3349: v0.107.0 released and recorded; TCL20 finished
  4fbb2081b ckpt 3348: pre-release: the GitHub engine can trade by itself: Settings > Cloud scan >
  85f56ee4d ckpt 3347: TCL20e: engine scan study segments + engine-grade.yml grader + GradeMain + 
  2469c3107 ckpt 3346: TCL20d ticked: engine trading (paper/live, Kelly, port, UI, phone auto-bet 
  633957eee ckpt 3345: TCL20d: CloudNovigPort (IOC by REST, lost-answer lookup, stop on location/k
  81d9bcf12 ckpt 3344: TCL20d paper trading: CloudTrader tested (15 cases), engine hook + heartbea
  d0fb5137a ckpt 3343: TCL20d: CloudKelly (tested), CloudTrader+ledger+order port written, gates c
  c0e26d2e1 ckpt 3342: Took the lease by force (Tj: other session archived); merged origin/main wi
  ae727c03a ckpt 3341: TCL20c done: trading key swap+share (CloudTradeKey, SharedTradeVault, Cloud
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
