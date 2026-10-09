# CHECKPOINT 2781 — read me first, then TASKS.md

**Written:** 2026-10-09T07:31:52Z · **tests:** all 4 fast checks green
**Branch:** `ccr-1dbf7537-vyxqtx` · **builds on:** `876f177f` (this checkpoint is the commit after it)

## Just done
pre-release: v0.82.0: SportsGameOdds Pro: key list (rotated like the others), Test key + sample, one switch that makes it the source for the scan, bids, open-bet pricing, closing lines/CLV (old bets too) and grading, rests the paid feeds it replaces, feeds the paper lab; SPORTSGAMEODDS_API.md (versionCode 146, v0.82.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.82.0), then run: bash tools/record-release.sh v0.82.0 146 "v0.82.0: SportsGameOdds Pro: key list (rotated like the others), Test key + sample, one switch that makes it the source for the scan, bids, open-bet pricing, closing lines/CLV (old bets too) and grading, rests the paid feeds it replaces, feeds the paper lab; SPORTSGAMEODDS_API.md"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  351f53e1 ckpt 2780: SGO: closes (CLV incl. old bets), scores+chained grading, key test, Settings
  439fbb03 ckpt 2779: SGO: research doc SPORTSGAMEODDS_API.md, parser/convert/client/sources + 13 
  4c91521d ckpt 2778: pre-release: v0.81.3: fixes the crash on open (R8 optimizer produced AutoSca
  f7cfc329 ckpt 2777: pre-ship: v0.81.3: fixes the crash on open (R8 optimizer produced AutoScanne
  b2597ee9 ckpt 2776: pre-release: v0.81.2: safe start: research recorders are switched off at eve
  db9f4fae ckpt 2775: pre-ship: v0.81.2: safe start: research recorders are switched off at every 
  ecadf495 ckpt 2774: pre-ship: v0.81.2: safe start: research recorders are switched off at every 
  625b9d1f ckpt 2773: pre-release: v0.81.1: crash-on-open hotfix: background research can no longe
  ac74102d ckpt 2772: pre-ship: v0.81.1: crash-on-open hotfix: background research can no longer t
  12960c64 ckpt 2771: pre-release: v0.81.0: Research mode page in Settings (paper lab found), Pinn
```

(13 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
