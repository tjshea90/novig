# CHECKPOINT 2476 — read me first, then TASKS.md

**Written:** 2026-10-03T20:47:07Z · **tests:** all 3 fast checks green
**Branch:** `ccr-e9ab2595-yxxu6w` · **builds on:** `61832433` (this checkpoint is the commit after it)

## Just done
BO2/BO3 (part): v0.56.1 file re-read (ANR 15:52 main thread in PlacedIndex.has->pickOf; 8 scanner switches + Pause queued rebuilds of feedOf in _state.update on Main; applySettings awaited repriceNow which waits on the scan's mutex). Fixed: pickOf/gameKey/pickKey memoized (tests+mutants), FeedBuild.kt publishResult/refeed/reindex off-main (FeedBuildTest, mutants killed), applySettings publishes settings first + latest-wins reprice job, MakerDesk.cycle keepPosting stops a pass on Pause, MakerUi lists cached

## Do this next
BO3: sweep other main-thread work in auto-bid paths (diagnostics, CNO list has()); BO4 fills answer + any fill fix; then BP1 map of the scan data

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  d9d7fe90 ckpt 2475: BP: Tj's resume-other-session + Scan Study log feature request written to TA
  12e771ff ckpt 2474: BO1 read: ANR at 15:52 = main thread in PlacedIndex.has->BetGrader.pickOf (r
  d2e98a7f ckpt 2473: BO: Tj's auto-bid lag / slow pause / no fills report written to TASKS.md (BO
  6eedaee9 ckpt 2472: full test shipped: v0.56.1 (code 97) released + recorded (release.yml run 37
  a6cb53bf ckpt 2471: pre-release: v0.56.1: full test: Bids tab shows the trap guard's move switch
  bb6ee206 ckpt 2470: full test done: floor 1,852 passed/23 skipped (exit 0, output checked), 109 
  007465de ckpt 2469: full test (in progress): floor 1,849 passed/23 skipped + 109 screenshots loo
  c02508d9 ckpt 2468: BN done: Novig pays no maker credit pregame on game markets (terms §2, fees
  8ce66782 ckpt 2467: BN: Tj's 'reconsider whether novig pays maker credit pregame' written to TAS
  463896c4 ckpt 2466: BM done: another AI's report checked (RESEARCH.md §73, 20 claims); novig_dr
```

(12 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
