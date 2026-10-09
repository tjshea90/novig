# CHECKPOINT 2774 — read me first, then TASKS.md

**Written:** 2026-10-09T04:38:49Z · **tests:** all 4 fast checks green
**Branch:** `ccr-1dbf7537-vyxqtx` · **builds on:** `ba89db9c` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.81.2: safe start: research recorders are switched off at every app start and, after a crash, bids and auto-bet too, so the app always opens to Settings

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M app/build.gradle.kts
    ?? data/src/test/kotlin/com/tjshea/vigilant/data/scanner/SafeStartTest.kt

## Last ten checkpoints
```
  625b9d1f ckpt 2773: pre-release: v0.81.1: crash-on-open hotfix: background research can no longe
  ac74102d ckpt 2772: pre-ship: v0.81.1: crash-on-open hotfix: background research can no longer t
  12960c64 ckpt 2771: pre-release: v0.81.0: Research mode page in Settings (paper lab found), Pinn
  6035c3db ckpt 2770: pre-ship: v0.81.0: Research mode page in Settings (paper lab found), Pinnodd
  dfc52625 ckpt 2769: RESEARCH.md 120.7: hockey wording corrected (first re-quote 4.6 s, full adju
  5ab289b7 ckpt 2768: QB: bigger live sample analysed (RESEARCH.md 120.7): guard keeps make-bid ed
  800bcabe ckpt 2767: pre-release: v0.80.0: Research mode (one switch for every recorder that plac
  b424db07 ckpt 2766: pre-ship: v0.80.0: Research mode (one switch for every recorder that places 
  9f91a262 ckpt 2765: pre-release: v0.79.0: paper lab (ladder covers, late-game tail strikes, alte
  8c4b9c07 ckpt 2764: pre-ship: v0.79.0: paper lab (ladder covers, late-game tail strikes, alterna
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
