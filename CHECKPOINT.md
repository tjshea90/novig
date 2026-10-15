# CHECKPOINT 3354 — read me first, then TASKS.md

**Written:** 2026-10-15T14:33:01Z · **tests:** all 5 fast checks green
**Branch:** `ccr-5182c4e7-h37dlu` · **builds on:** `8aea43261` (this checkpoint is the commit after it)

## Just done
pre-release: the phone shows what the GitHub engine did (wallet, orders, wins and losses) on open, the engine sleeps while the trading wallet is under 50 cents, and the rotation covers a missed account slot (versionCode 287, v0.108.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.108.0), then run: bash tools/record-release.sh v0.108.0 287 "the phone shows what the GitHub engine did (wallet, orders, wins and losses) on open, the engine sleeps while the trading wallet is under 50 cents, and the rotation covers a missed account slot"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  8d34f27e6 ckpt 3353: TCL21 written into TASKS.md; recommendations_2026-10-15.md (ParlayAPI/OddsA
  477b459bd ckpt 3352: phone syncs the engine's orders/results/wallet on open (trades.json loop, T
  df97cc223 ckpt 3351: engine empty-wallet sleep (CloudEngine.walletProbe, status.walletSleep, Set
  3a82a6ef0 ckpt 3350: rotation: missed-slot detector + checkin/cover + routine repo step, tested
  17bc01bf7 ckpt 3349: v0.107.0 released and recorded; TCL20 finished
  4fbb2081b ckpt 3348: pre-release: the GitHub engine can trade by itself: Settings > Cloud scan >
  85f56ee4d ckpt 3347: TCL20e: engine scan study segments + engine-grade.yml grader + GradeMain + 
  2469c3107 ckpt 3346: TCL20d ticked: engine trading (paper/live, Kelly, port, UI, phone auto-bet 
  633957eee ckpt 3345: TCL20d: CloudNovigPort (IOC by REST, lost-answer lookup, stop on location/k
  81d9bcf12 ckpt 3344: TCL20d paper trading: CloudTrader tested (15 cases), engine hook + heartbea
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
