# CHECKPOINT 2821 — read me first, then TASKS.md

**Written:** 2026-10-10T03:28:59Z · **tests:** all 4 fast checks green
**Branch:** `ccr-bab5067e-obkelv` · **builds on:** `49c92646` (this checkpoint is the commit after it)

## Just done
SV done: investigation of live betting on Novig. RESEARCH.md §123 + NOVIG_API.md §22 written; tool tools/research/novig_live_sitting.py; evidence in research/live_sitting_2026-10-10/ (462 live books, order survival, maker re-sim at 5.3 s place/cancel delay). Findings: thin live ladders, maker side survives Novig's ~5 s in-play delay, taker side not; NCAAF live fee is 0.06 not 0.03; Pinnodds trial ends 2026-10-10 23:34Z

## Do this next
WAIT for Tj's answer to SV5: 'build it' = ship paper-only SV6 (order-timing probe) + SV7 (live maker lab v2) today. Nothing in the app was changed.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  91487752 ckpt 2820: SV1: logged Tj's live-betting investigation request (TASKS.md SV); read RESE
  6b159526 ckpt 2819: SU1-SU3 done: 5 UI design candidates (Desk, Expressive, Signal, Daylight, Ne
  569228d6 ckpt 2818: SU1 in progress: baseline Roborazzi render works locally (app/screenshots/1_
  80722a11 ckpt 2817: logged Tj's UI design-candidates request as TASKS.md SU
  51247364 ckpt 2816: Added Tj's standing setup rules (2026-10-10: use plugins/skills/connectors, 
  51d99f74 ckpt 2815: pre-release: v0.84.4: Research running foreground service keeps the paper la
  a4effd21 ckpt 2814: pre-ship: v0.84.4: Research running foreground service keeps the paper lab a
  6e74c425 ckpt 2813: pre-ship: v0.84.4: Research running foreground service keeps the paper lab a
  f6d25eb0 ckpt 2812: pre-release: v0.84.3: research switches stay on when Android ends the app; s
  5e2a8705 ckpt 2811: pre-ship: v0.84.3: research switches stay on when Android ends the app; safe
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
