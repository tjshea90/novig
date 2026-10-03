# CHECKPOINT 2477 — read me first, then TASKS.md

**Written:** 2026-10-03T20:50:11Z · **tests:** all 3 fast checks green
**Branch:** `ccr-e9ab2595-yxxu6w` · **builds on:** `b13fb332` (this checkpoint is the commit after it)

## Just done
BO2-BO4 done: lag/pause root causes fixed+tested (RESEARCH §74), fills answered, diagnostics now has last-24h bid-hours vs expected fills

## Do this next
BO5: run the full floor (bash tools/test.sh), ship v0.56.2 (ship.sh), trigger release.yml, record; THEN BP1 map the scan data

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  d0217e12 ckpt 2476: BO2/BO3 (part): v0.56.1 file re-read (ANR 15:52 main thread in PlacedIndex.h
  d9d7fe90 ckpt 2475: BP: Tj's resume-other-session + Scan Study log feature request written to TA
  12e771ff ckpt 2474: BO1 read: ANR at 15:52 = main thread in PlacedIndex.has->BetGrader.pickOf (r
  d2e98a7f ckpt 2473: BO: Tj's auto-bid lag / slow pause / no fills report written to TASKS.md (BO
  6eedaee9 ckpt 2472: full test shipped: v0.56.1 (code 97) released + recorded (release.yml run 37
  a6cb53bf ckpt 2471: pre-release: v0.56.1: full test: Bids tab shows the trap guard's move switch
  bb6ee206 ckpt 2470: full test done: floor 1,852 passed/23 skipped (exit 0, output checked), 109 
  007465de ckpt 2469: full test (in progress): floor 1,849 passed/23 skipped + 109 screenshots loo
  c02508d9 ckpt 2468: BN done: Novig pays no maker credit pregame on game markets (terms §2, fees
  8ce66782 ckpt 2467: BN: Tj's 'reconsider whether novig pays maker credit pregame' written to TAS
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
