# CHECKPOINT 2549 — read me first, then TASKS.md

**Written:** 2026-10-05T16:32:38Z · **tests:** all 3 fast checks green
**Branch:** `ccr-fef46304-swa9m4` · **builds on:** `d9aee8d1` (this checkpoint is the commit after it)

## Just done
wrote the work order and CI1 finding into TASKS.md (kill switch first, then bids, then Pinnacle-only; two releases)

## Do this next
CL1: add ScanSettings.killed + derived paused (stored field pausedSwitch), kill/resume in MainViewModel, red STOP bar in WalletStrip on every tab, floating widget + notification actions, services stop, tests

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  10c64bc3 ckpt 2548: CJ1 done: Tracker profit/staked/chart count every settled bet in both views 
  7c5301a7 ckpt 2547: wrote Tj's 2026-10-05 Pinnacle-only / Novig-only profit / auto-bid / kill-sw
  0b8ca56c ckpt 2546: CH3 done: v0.63.0 released and recorded (guard covers any two-outcome holdin
  55d1ac02 ckpt 2545: pre-release: v0.63.0: the grading guard covers any market held on both sides
  503a49b0 ckpt 2544: CH1-CH2 done: investigation (RESEARCH §87.1): 8 silence-rule grades, 7 righ
  0cf7e469 ckpt 2543: CG3 done: v0.62.0 released and recorded (the Ollie Gordon lock grade fix); L
  cca73357 ckpt 2542: pre-release: v0.62.0: a market held on both sides is never graded lost from 
  afa63093 ckpt 2541: pre-ship: v0.62.0: a market held on both sides is never graded lost from Nov
  eac3888a ckpt 2540: CG1-CG2 done: the -$2.29 was Ollie Gordon's lock leg (Over 29.5, won with 10
  2ed52077 ckpt 2539: wrote Tj's 2026-10-05 'locked in negative profit' report into TASKS.md (CG1-
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
