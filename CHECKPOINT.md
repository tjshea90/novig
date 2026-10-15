# CHECKPOINT 3352 — read me first, then TASKS.md

**Written:** 2026-10-15T14:28:06Z · **tests:** all 5 fast checks green
**Branch:** `ccr-5182c4e7-h37dlu` · **builds on:** `5b9b39ac8` (this checkpoint is the commit after it)

## Just done
phone syncs the engine's orders/results/wallet on open (trades.json loop, TradeSummary, 'What the engine did' card, status.walletCash); empty-wallet sleep done

## Do this next
key/usage advisor (research + in-app list), TASKS.md entries, then full tests + ship v0.108.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  df97cc223 ckpt 3351: engine empty-wallet sleep (CloudEngine.walletProbe, status.walletSleep, Set
  3a82a6ef0 ckpt 3350: rotation: missed-slot detector + checkin/cover + routine repo step, tested
  17bc01bf7 ckpt 3349: v0.107.0 released and recorded; TCL20 finished
  4fbb2081b ckpt 3348: pre-release: the GitHub engine can trade by itself: Settings > Cloud scan >
  85f56ee4d ckpt 3347: TCL20e: engine scan study segments + engine-grade.yml grader + GradeMain + 
  2469c3107 ckpt 3346: TCL20d ticked: engine trading (paper/live, Kelly, port, UI, phone auto-bet 
  633957eee ckpt 3345: TCL20d: CloudNovigPort (IOC by REST, lost-answer lookup, stop on location/k
  81d9bcf12 ckpt 3344: TCL20d paper trading: CloudTrader tested (15 cases), engine hook + heartbea
  d0fb5137a ckpt 3343: TCL20d: CloudKelly (tested), CloudTrader+ledger+order port written, gates c
  c0e26d2e1 ckpt 3342: Took the lease by force (Tj: other session archived); merged origin/main wi
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
