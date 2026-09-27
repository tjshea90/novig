# CHECKPOINT 467 — read me first, then TASKS.md

**Written:** 2026-09-27T03:05:46Z · **tests:** all 1 fast checks green
**Branch:** `claude/cno-scanner-enhancements-tj52i1` · **builds on:** `84884cd` (this checkpoint is the commit after it)

## Just done
N1 VM wiring done: markPlaced logs every check (Vigilant item -> tracker.track(o,1.0,key); CNO -> tracker.logCno + async NovigBetFinder ids), unmarkPlaced -> tracker.untrack, one-time importPlaced on init; compiles

## Do this next
Run app tests; tick N1 in TASKS.md; then N2 BetSettler (Novig market outcome status, pacing, resolve ids) + tests, WorkManager 3h job; then N4 check odds now, N5 Stats|Bets UI, N6 full tests + ship v0.15.6

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  081658e ckpt 466: N1 data layer done: BetTracker.logCno/untrack/edit/setStake/importPlaced + tes
  94081ed ckpt 465: v0.15.5 released+recorded (link sent). N1 step 1 done: TrackedBet model extend
  e00442e ckpt 464: N-plan: full design for tracker/stats written into TASKS.md (N3 answered: not 
  7d00e24 ckpt 463: Logged Tj's tracker/stats request as N1-N6; v0.15.5 release building
  3f75dde ckpt 462: pre-release: v0.15.5: full test: the Novig stream no longer reports a failure 
  405f267 ckpt 461: Full test (Tj 02:36Z): floor 474->480 green; fixed (each test failed on the ol
  9b34b97 ckpt 460: M1-M3: live +EV findings in RESEARCH 21 (not feasible on free feeds; measured)
  839495c ckpt 459: v0.15.4 released and recorded (L1-L4 done)
  9ebfebe ckpt 458: pre-release: v0.15.4: bet slips open without CNO (Novig's own catalog, 60/60 e
  d1ad63e ckpt 457: L1-L3 done; v0.15.4 code 24; forced floor 474 green (engine 39, data 305, app 
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
