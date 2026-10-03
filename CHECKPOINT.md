# CHECKPOINT 2474 — read me first, then TASKS.md

**Written:** 2026-10-03T20:07:00Z · **tests:** all 3 fast checks green
**Branch:** `ccr-c4435189-1cfj54` · **builds on:** `d2e98a7f` (this checkpoint is the commit after it)

## Just done
BO1 read: ANR at 15:52 = main thread in PlacedIndex.has->BetGrader.pickOf (regex; ~70-110us/call on JVM, worse on ART); applySettings/applyReport/repriceNow/recheck run feedOf (has per feed row) inside _state.update on Main; bids rest 1 min median (163 'about to expire: re-posted', 48 'fair goes old'), wallet ~$12 caps ~10 bids while 300-450 wait, 0 fills of 320. Workflow wf_7de17a06-a5d investigating (main-thread, maker-loop, pause-path, fills, diag-gaps)

## Do this next
BO2: read workflow results, then fix: memoize pickOf/identity, move feedOf off Main, pause publishes first; bids: rest longer (fair refresh instead of expiry), see fills findings

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  d2e98a7f ckpt 2473: BO: Tj's auto-bid lag / slow pause / no fills report written to TASKS.md (BO
  6eedaee9 ckpt 2472: full test shipped: v0.56.1 (code 97) released + recorded (release.yml run 37
  a6cb53bf ckpt 2471: pre-release: v0.56.1: full test: Bids tab shows the trap guard's move switch
  bb6ee206 ckpt 2470: full test done: floor 1,852 passed/23 skipped (exit 0, output checked), 109 
  007465de ckpt 2469: full test (in progress): floor 1,849 passed/23 skipped + 109 screenshots loo
  c02508d9 ckpt 2468: BN done: Novig pays no maker credit pregame on game markets (terms §2, fees
  8ce66782 ckpt 2467: BN: Tj's 'reconsider whether novig pays maker credit pregame' written to TAS
  463896c4 ckpt 2466: BM done: another AI's report checked (RESEARCH.md §73, 20 claims); novig_dr
  2f50f400 ckpt 2465: BM: Tj's request to vet another AI's CLV/EV report written to TASKS.md (BM1-
  0b8eaa2f ckpt 2464: BL done: v0.56.0 (code 96) released + recorded (sharp veto bar 1%, Kelly cap
```
