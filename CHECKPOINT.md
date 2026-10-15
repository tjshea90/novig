# CHECKPOINT 3333 — read me first, then TASKS.md

**Written:** 2026-10-15T04:44:42Z · **tests:** all 5 fast checks green
**Branch:** `ccr-4dcbaa5f-skgw6q` · **builds on:** `7b2c4bf69` (this checkpoint is the commit after it)

## Just done
TCL19a: tools/coord.py lease+board on the claude-coord branch, push.sh guard (exit 4 only when another live session holds it, fails open), resume.sh COORD line, PROTOCOL.md, CLAUDE.md section, ROUTINE.md step 0; tools/test_coord.sh 24 checks pass

## Do this next
Check the claude-coord branch exists on GitHub and this session holds the lease; update the daily review trigger prompt (acquire --kind routine first); write research/autonomous_engine_2026-10-15.md; answer Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M CLAUDE.md
     M research/review/ROUTINE.md

## Last ten checkpoints
```
  82cb8f4d4 ckpt 3332: TCL19 written (Tj's words): three accounts + autonomous GitHub engine
  270e385f0 ckpt 3331: TCL16 measured on v0.106.1: full scan 621 s at 16/s, hot re-reads 844 (8%),
  e7eb1640b ckpt 3330: TCL18c: daily scheduled task created (trig_011QGvat6eD9ER3bfE59T51T, 08:55 
  61f877868 ckpt 3329: TCL18c/e: first digest read and written up (research/review/2026-10-15.md, 
  2d8800dbf ckpt 3328: TCL18c: research/review/ROUTINE.md (the scheduled review's rules: read the 
  837adc5f4 ckpt 3327: TCL18b: review job built (cloud ReviewMain runs the app's own StudyExport o
  75db6610e ckpt 3326: TCL18 written: scheduled diagnostics + profit review + automatic delivery (
  de2630c8a ckpt 3325: v0.106.1 shipped (3690 tests green), release.yml triggered, engine run 3867
  dbdb32f50 ckpt 3324: pre-release: v0.106.1: the GitHub engine's hot refresh no longer starves th
  753e1222d ckpt 3323: TCL16d: HotBudget (hot re-reads sized to a quarter of the route's measured 
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
