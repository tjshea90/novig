# CHECKPOINT 466 — read me first, then TASKS.md

**Written:** 2026-09-27T03:04:01Z · **tests:** all 1 fast checks green
**Branch:** `claude/cno-scanner-enhancements-tj52i1` · **builds on:** `f2fdb2c` (this checkpoint is the commit after it)

## Just done
N1 data layer done: BetTracker.logCno/untrack/edit/setStake/importPlaced + tests (BetTrackerTest 8 green)

## Do this next
N1 VM wiring: markPlaced -> log (CNO: c.tracker.logCno(item.cno.row, item.cno.ev, item.cno.live, item.key) + resolve outcome via betFinder async and edit ids; Vigilant item: find Opportunity in state.result by key -> tracker.track(o,1.0,item.key)); unmarkPlaced -> tracker.untrack(key); VM init: tracker.importPlaced(placed.load().bets) once. Then N2 BetSettler

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  94081ed ckpt 465: v0.15.5 released+recorded (link sent). N1 step 1 done: TrackedBet model extend
  e00442e ckpt 464: N-plan: full design for tracker/stats written into TASKS.md (N3 answered: not 
  7d00e24 ckpt 463: Logged Tj's tracker/stats request as N1-N6; v0.15.5 release building
  3f75dde ckpt 462: pre-release: v0.15.5: full test: the Novig stream no longer reports a failure 
  405f267 ckpt 461: Full test (Tj 02:36Z): floor 474->480 green; fixed (each test failed on the ol
  9b34b97 ckpt 460: M1-M3: live +EV findings in RESEARCH 21 (not feasible on free feeds; measured)
  839495c ckpt 459: v0.15.4 released and recorded (L1-L4 done)
  9ebfebe ckpt 458: pre-release: v0.15.4: bet slips open without CNO (Novig's own catalog, 60/60 e
  d1ad63e ckpt 457: L1-L3 done; v0.15.4 code 24; forced floor 474 green (engine 39, data 305, app 
  3021174 ckpt 456: Logged Tj's live +EV request as M1-M3 (next version, after L4 ships); L3 in pr
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
