# CHECKPOINT 2777 — read me first, then TASKS.md

**Written:** 2026-10-09T04:55:09Z · **tests:** all 4 fast checks green
**Branch:** `ccr-1dbf7537-vyxqtx` · **builds on:** `5e5e1fc4` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.81.3: fixes the crash on open (R8 optimizer produced AutoScanner code the phone rejected; optimizer off) plus safe start (research off at every start, bids/auto-bet off after a crash)

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M app/build.gradle.kts
     M app/proguard-rules.pro

## Last ten checkpoints
```
  b2597ee9 ckpt 2776: pre-release: v0.81.2: safe start: research recorders are switched off at eve
  db9f4fae ckpt 2775: pre-ship: v0.81.2: safe start: research recorders are switched off at every 
  ecadf495 ckpt 2774: pre-ship: v0.81.2: safe start: research recorders are switched off at every 
  625b9d1f ckpt 2773: pre-release: v0.81.1: crash-on-open hotfix: background research can no longe
  ac74102d ckpt 2772: pre-ship: v0.81.1: crash-on-open hotfix: background research can no longer t
  12960c64 ckpt 2771: pre-release: v0.81.0: Research mode page in Settings (paper lab found), Pinn
  6035c3db ckpt 2770: pre-ship: v0.81.0: Research mode page in Settings (paper lab found), Pinnodd
  dfc52625 ckpt 2769: RESEARCH.md 120.7: hockey wording corrected (first re-quote 4.6 s, full adju
  5ab289b7 ckpt 2768: QB: bigger live sample analysed (RESEARCH.md 120.7): guard keeps make-bid ed
  800bcabe ckpt 2767: pre-release: v0.80.0: Research mode (one switch for every recorder that plac
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
