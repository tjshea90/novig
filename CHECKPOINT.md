# CHECKPOINT 2481 — read me first, then TASKS.md

**Written:** 2026-10-03T21:19:28Z · **tests:** all 3 fast checks green
**Branch:** `ccr-e9ab2595-yxxu6w` · **builds on:** `14b896bc` (this checkpoint is the commit after it)

## Just done
BP3/BP4: data/study done (model, journal, ScanStudy observe/enrich/settle, StudyExport, GuardedCloses; 16 tests green) + app wiring compiled (container collectors, settleStudy, share intent, Settings button+switch+note, Diagnostics line, SettleWorker hook)

## Do this next
write app tests (Settings UI, share flow, Vigilant observe, wiring pins), mutants, full floor, docs (BRIEF/RESEARCH/TASKS), ship v0.57.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  bfc6c4cb ckpt 2480: BO done and shipped (v0.56.2 released+recorded). BP1 mapped; BP2/BP3 in prog
  756061ed ckpt 2479: pre-release: v0.56.2: lag fix: the +EV feed is built off the main thread (no
  cb44ab24 ckpt 2478: v0.56.2 prepared: version 98, BRIEF note; floor green (1,863 passed, 23 skip
  03ca5c35 ckpt 2477: BO2-BO4 done: lag/pause root causes fixed+tested (RESEARCH §74), fills answ
  d0217e12 ckpt 2476: BO2/BO3 (part): v0.56.1 file re-read (ANR 15:52 main thread in PlacedIndex.h
  d9d7fe90 ckpt 2475: BP: Tj's resume-other-session + Scan Study log feature request written to TA
  12e771ff ckpt 2474: BO1 read: ANR at 15:52 = main thread in PlacedIndex.has->BetGrader.pickOf (r
  d2e98a7f ckpt 2473: BO: Tj's auto-bid lag / slow pause / no fills report written to TASKS.md (BO
  6eedaee9 ckpt 2472: full test shipped: v0.56.1 (code 97) released + recorded (release.yml run 37
  a6cb53bf ckpt 2471: pre-release: v0.56.1: full test: Bids tab shows the trap guard's move switch
```

(13 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
