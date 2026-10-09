# CHECKPOINT 2776 — read me first, then TASKS.md

**Written:** 2026-10-09T04:45:01Z · **tests:** all 4 fast checks green
**Branch:** `ccr-1dbf7537-vyxqtx` · **builds on:** `db9f4fae` (this checkpoint is the commit after it)

## Just done
pre-release: v0.81.2: safe start: research recorders are switched off at every app start and, after a crash, bids and auto-bet too, so the app always opens to Settings (versionCode 144, v0.81.2)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.81.2), then run: bash tools/record-release.sh v0.81.2 144 "v0.81.2: safe start: research recorders are switched off at every app start and, after a crash, bids and auto-bet too, so the app always opens to Settings"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  db9f4fae ckpt 2775: pre-ship: v0.81.2: safe start: research recorders are switched off at every 
  ecadf495 ckpt 2774: pre-ship: v0.81.2: safe start: research recorders are switched off at every 
  625b9d1f ckpt 2773: pre-release: v0.81.1: crash-on-open hotfix: background research can no longe
  ac74102d ckpt 2772: pre-ship: v0.81.1: crash-on-open hotfix: background research can no longer t
  12960c64 ckpt 2771: pre-release: v0.81.0: Research mode page in Settings (paper lab found), Pinn
  6035c3db ckpt 2770: pre-ship: v0.81.0: Research mode page in Settings (paper lab found), Pinnodd
  dfc52625 ckpt 2769: RESEARCH.md 120.7: hockey wording corrected (first re-quote 4.6 s, full adju
  5ab289b7 ckpt 2768: QB: bigger live sample analysed (RESEARCH.md 120.7): guard keeps make-bid ed
  800bcabe ckpt 2767: pre-release: v0.80.0: Research mode (one switch for every recorder that plac
  b424db07 ckpt 2766: pre-ship: v0.80.0: Research mode (one switch for every recorder that places 
```
