# CHECKPOINT 2765 — read me first, then TASKS.md

**Written:** 2026-10-09T03:00:23Z · **tests:** all 4 fast checks green
**Branch:** `ccr-1dbf7537-vyxqtx` · **builds on:** `8c4b9c07` (this checkpoint is the commit after it)

## Just done
pre-release: v0.79.0: paper lab (ladder covers, late-game tail strikes, alternate lines vs Pinnacle's, graded from settled markets), Settings > Paper lab, Diagnostics block; research tools and RESEARCH.md 120-121 (versionCode 140, v0.79.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.79.0), then run: bash tools/record-release.sh v0.79.0 140 "v0.79.0: paper lab (ladder covers, late-game tail strikes, alternate lines vs Pinnacle's, graded from settled markets), Settings > Paper lab, Diagnostics block; research tools and RESEARCH.md 120-121"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  8c4b9c07 ckpt 2764: pre-ship: v0.79.0: paper lab (ladder covers, late-game tail strikes, alterna
  247e795f ckpt 2763: QD4-QD5: paper lab wired (Settings, Diagnostics), RESEARCH.md 121 catalogue,
  3b09dfcb ckpt 2762: QD1-QD3 data layer: LadderScan, TailModel/TailScan, AltLineScan, LabPaper/La
  52dd99b5 ckpt 2761: QC: alternate lines recorded + analysed (RESEARCH.md 120.6), ladder tool, re
  9a241650 ckpt 2760: QB: pregame tape too thin (7 min), saved; second live recording running to 0
  1afcb4af ckpt 2759: QB: first live study saved (research/pinnodds_2026-10-08b/, RESEARCH.md 120.
  3c089c8f ckpt 2758: QB5a: pinn_novig_maker.py (resting-bid simulation from Pinnacle fair + Novig
  efea5b9e ckpt 2757: QB4: outside-the-box section [G] in pinn_novig_deep.py (lead-lag, imbalance,
  7ed19259 ckpt 2756: QB: wrote tools/research/pinn_novig_deep.py (deep read of the Pinnacle/Novig
  55d736b8 ckpt 2755: QA1-QA3 read against Tj's v0.78.0 diagnostics (RESEARCH.md §120.1)
```
