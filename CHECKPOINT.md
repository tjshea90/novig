# CHECKPOINT 2374 — read me first, then TASKS.md

**Written:** 2026-10-02T19:22:17Z · **tests:** all 3 fast checks green
**Branch:** `ccr-38dc4f3b-3f0cld` · **builds on:** `767b785e` (this checkpoint is the commit after it)

## Just done
floor had 2 StickyHeadersTest fails (Novig-only row made the pinned bar too tall): chip moved to the top bar, note into the list; lock live-fee uses the earlier of Novig's/Tracker's start (+test); screenshots checked

## Do this next
full floor, then CI, ship v0.47.0, release, answer Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  3a659c13 ckpt 2373: AY6 done + docs: NovigNow, Tracker Novig-only chip, diagnostics fields, NOVI
  cb1d3514 ckpt 2372: AY6 code: TrackedBet novigFair/novigAtMs/novigClose(+At), NovigNow (mid, app
  0cacac9f ckpt 2371: AY3-AY5 done: lock scanner, by-hand lock card, auto-lock, stats; LockAppTest
  d3f03028 ckpt 2370: AY4/AY5 UI: LockCard + confirm on the bet sheet, Tracker lock badge, Auto-be
  568d448b ckpt 2369: AY3/AY4 app wiring: LockScanner + AutoLocker (cycle hook, notification), UiS
  2e25da47 ckpt 2368: AY4 data side: placeLock (FOK, positions check), LockPositions, lockFor; Loc
  1f5b01ae ckpt 2367: AY2 done: LockIn math + LockInTest (property test over 20k cases), mutants 3
  a3e8603c ckpt 2366: AY1 done: RESEARCH.md §67 (lock-in plausible and exact on Novig: FOK, same 
  004e33fc ckpt 2365: AY: Tj's 18:50Z request (lock-in arbitrage on own Novig bets + auto-lock; Tr
  9781e664 ckpt 2364: AX done: v0.46.0 released, verified and recorded (settings reorganization, A
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
