# CHECKPOINT 2371 — read me first, then TASKS.md

**Written:** 2026-10-02T19:10:50Z · **tests:** all 3 fast checks green
**Branch:** `ccr-38dc4f3b-3f0cld` · **builds on:** `bce44396` (this checkpoint is the commit after it)

## Just done
AY3-AY5 done: lock scanner, by-hand lock card, auto-lock, stats; LockAppTest 4 + mutants 3/3

## Do this next
AY6: Tracker 'Novig only' filter: TrackedBet.novigFair/novigAtMs (+ novigClose), filled by the pricing pass (Opportunity bestBid+quote) and a Novig-only read (books per market), reuse when fresh; NovigOnly view transform for stats/EV; chip in Tracker; Check odds now → Novig-only when on

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  d3f03028 ckpt 2370: AY4/AY5 UI: LockCard + confirm on the bet sheet, Tracker lock badge, Auto-be
  568d448b ckpt 2369: AY3/AY4 app wiring: LockScanner + AutoLocker (cycle hook, notification), UiS
  2e25da47 ckpt 2368: AY4 data side: placeLock (FOK, positions check), LockPositions, lockFor; Loc
  1f5b01ae ckpt 2367: AY2 done: LockIn math + LockInTest (property test over 20k cases), mutants 3
  a3e8603c ckpt 2366: AY1 done: RESEARCH.md §67 (lock-in plausible and exact on Novig: FOK, same 
  004e33fc ckpt 2365: AY: Tj's 18:50Z request (lock-in arbitrage on own Novig bets + auto-lock; Tr
  9781e664 ckpt 2364: AX done: v0.46.0 released, verified and recorded (settings reorganization, A
  ef6301c3 ckpt 2363: pre-release: v0.46.0: Settings reorganized (a home list with search and plai
  6514a7e9 ckpt 2362: AX6 prep: v0.46.0 code 84; floor 1,710 had 3 data tests on old menu paths, f
  b94ccfe6 ckpt 2361: AX polish: home summaries (keys count, 1%+), CLV explained in presets intro,
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
