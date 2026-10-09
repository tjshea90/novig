# CHECKPOINT 2783 — read me first, then TASKS.md

**Written:** 2026-10-09T08:08:01Z · **tests:** all 4 fast checks green
**Branch:** `ccr-1dbf7537-vyxqtx` · **builds on:** `77afab48` (this checkpoint is the commit after it)

## Just done
pre-release: v0.83.0: GitHub research lab (lab module + lab-record.yml: scanner, paper bids, SGO tape/closes, no phone), SGO spec audit fixes (403/429/504), SGO for injuries and other-books, paper bids restore after a stop, toggle-off equivalence tests (versionCode 147, v0.83.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.83.0), then run: bash tools/record-release.sh v0.83.0 147 "v0.83.0: GitHub research lab (lab module + lab-record.yml: scanner, paper bids, SGO tape/closes, no phone), SGO spec audit fixes (403/429/504), SGO for injuries and other-books, paper bids restore after a stop, toggle-off equivalence tests"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  e3669839 ckpt 2782: SGO: spec fixes (403/429/closes 504), injuries+other-books via SGO, toggle-o
  3f89f9f4 ckpt 2781: pre-release: v0.82.0: SportsGameOdds Pro: key list (rotated like the others)
  351f53e1 ckpt 2780: SGO: closes (CLV incl. old bets), scores+chained grading, key test, Settings
  439fbb03 ckpt 2779: SGO: research doc SPORTSGAMEODDS_API.md, parser/convert/client/sources + 13 
  4c91521d ckpt 2778: pre-release: v0.81.3: fixes the crash on open (R8 optimizer produced AutoSca
  f7cfc329 ckpt 2777: pre-ship: v0.81.3: fixes the crash on open (R8 optimizer produced AutoScanne
  b2597ee9 ckpt 2776: pre-release: v0.81.2: safe start: research recorders are switched off at eve
  db9f4fae ckpt 2775: pre-ship: v0.81.2: safe start: research recorders are switched off at every 
  ecadf495 ckpt 2774: pre-ship: v0.81.2: safe start: research recorders are switched off at every 
  625b9d1f ckpt 2773: pre-release: v0.81.1: crash-on-open hotfix: background research can no longe
```

(11 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
