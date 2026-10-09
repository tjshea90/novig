# CHECKPOINT 2773 — read me first, then TASKS.md

**Written:** 2026-10-09T04:21:29Z · **tests:** all 4 fast checks green
**Branch:** `ccr-1dbf7537-vyxqtx` · **builds on:** `ac74102d` (this checkpoint is the commit after it)

## Just done
pre-release: v0.81.1: crash-on-open hotfix: background research can no longer take the app down (handler on the app scope, lab loop catches errors), paper-bid journal writes batched, last crash copied to Downloads/Vigilant (versionCode 143, v0.81.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.81.1), then run: bash tools/record-release.sh v0.81.1 143 "v0.81.1: crash-on-open hotfix: background research can no longer take the app down (handler on the app scope, lab loop catches errors), paper-bid journal writes batched, last crash copied to Downloads/Vigilant"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  ac74102d ckpt 2772: pre-ship: v0.81.1: crash-on-open hotfix: background research can no longer t
  12960c64 ckpt 2771: pre-release: v0.81.0: Research mode page in Settings (paper lab found), Pinn
  6035c3db ckpt 2770: pre-ship: v0.81.0: Research mode page in Settings (paper lab found), Pinnodd
  dfc52625 ckpt 2769: RESEARCH.md 120.7: hockey wording corrected (first re-quote 4.6 s, full adju
  5ab289b7 ckpt 2768: QB: bigger live sample analysed (RESEARCH.md 120.7): guard keeps make-bid ed
  800bcabe ckpt 2767: pre-release: v0.80.0: Research mode (one switch for every recorder that plac
  b424db07 ckpt 2766: pre-ship: v0.80.0: Research mode (one switch for every recorder that places 
  9f91a262 ckpt 2765: pre-release: v0.79.0: paper lab (ladder covers, late-game tail strikes, alte
  8c4b9c07 ckpt 2764: pre-ship: v0.79.0: paper lab (ladder covers, late-game tail strikes, alterna
  247e795f ckpt 2763: QD4-QD5: paper lab wired (Settings, Diagnostics), RESEARCH.md 121 catalogue,
```
