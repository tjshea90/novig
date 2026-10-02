# CHECKPOINT 2369 — read me first, then TASKS.md

**Written:** 2026-10-02T19:05:09Z · **tests:** all 3 fast checks green
**Branch:** `ccr-38dc4f3b-3f0cld` · **builds on:** `11e8cc38` (this checkpoint is the commit after it)

## Just done
AY3/AY4 app wiring: LockScanner + AutoLocker (cycle hook, notification), UiState.locks/locking, MainViewModel.scanLocks (Tracker open, after Check odds now)/lockIn (confirmed amount floor); stats keep locks out of record/EV/CLV

## Do this next
UI: Tracker badge + BetSheet LockCard with confirm; Auto-bet tab 'Lock in profits' section; SettingsIndex entries; tests (LockScanner via fake Novig, UI); then AY6 Novig-only filter

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/src/main/kotlin/com/tjshea/vigilant/app/MainViewModel.kt

## Last ten checkpoints
```
  2e25da47 ckpt 2368: AY4 data side: placeLock (FOK, positions check), LockPositions, lockFor; Loc
  1f5b01ae ckpt 2367: AY2 done: LockIn math + LockInTest (property test over 20k cases), mutants 3
  a3e8603c ckpt 2366: AY1 done: RESEARCH.md §67 (lock-in plausible and exact on Novig: FOK, same 
  004e33fc ckpt 2365: AY: Tj's 18:50Z request (lock-in arbitrage on own Novig bets + auto-lock; Tr
  9781e664 ckpt 2364: AX done: v0.46.0 released, verified and recorded (settings reorganization, A
  ef6301c3 ckpt 2363: pre-release: v0.46.0: Settings reorganized (a home list with search and plai
  6514a7e9 ckpt 2362: AX6 prep: v0.46.0 code 84; floor 1,710 had 3 data tests on old menu paths, f
  b94ccfe6 ckpt 2361: AX polish: home summaries (keys count, 1%+), CLV explained in presets intro,
  38b97032 ckpt 2360: AX3-AX5 code + existing tests moved: Settings home/pages/search, Auto-bet ta
  02e24ceb ckpt 2359: AX3/AX4 code in progress: SettingsPage home+pages+search (SettingsIndex), Au
```

(9 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
