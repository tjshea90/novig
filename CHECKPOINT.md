# CHECKPOINT 470 — read me first, then TASKS.md

**Written:** 2026-09-27T03:12:54Z · **tests:** all 1 fast checks green
**Branch:** `claude/cno-scanner-enhancements-tj52i1` · **builds on:** `51cf41e` (this checkpoint is the commit after it)

## Just done
N4 data+VM done: BetRecheck (CNO game page -> CnoBooks.check fair -> nowFair/nowEv/nowBooks, closing before start, paced, respects pause), observe() also sets nowEv for Vigilant bets, container.recheck, VM checkOdds() + UiState.checkingOdds; BetRecheckTest 3 green; compiles

## Do this next
N5 Tracker UI: Stats|Bets switch, period chips All/30d/7d/Today, CNO vs Vigilant split, win% W-L-P, profit/ROI green/red, Bets filters Open/Settled/All, stake dialog (vm.setStake), Check odds now button (vm.checkOdds), now-EV green/red on cards, LaunchedEffect vm.settleBets; wire in MainActivity:542; extend TrackerStats (won/lost/pushed/winRate); screenshots; then N6 full tests + ship v0.15.6

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  3db6c81 ckpt 469: N2 wired: container.settler (novig.market + betFinder.findEnded), SettleWorker
  fb3be0b ckpt 468: N2 data done: BetSettler (Novig outcome WIN/LOSS/PUSH/FMV -> tracked result, 1
  9eb8506 ckpt 467: N1 VM wiring done: markPlaced logs every check (Vigilant item -> tracker.track
  081658e ckpt 466: N1 data layer done: BetTracker.logCno/untrack/edit/setStake/importPlaced + tes
  94081ed ckpt 465: v0.15.5 released+recorded (link sent). N1 step 1 done: TrackedBet model extend
  e00442e ckpt 464: N-plan: full design for tracker/stats written into TASKS.md (N3 answered: not 
  7d00e24 ckpt 463: Logged Tj's tracker/stats request as N1-N6; v0.15.5 release building
  3f75dde ckpt 462: pre-release: v0.15.5: full test: the Novig stream no longer reports a failure 
  405f267 ckpt 461: Full test (Tj 02:36Z): floor 474->480 green; fixed (each test failed on the ol
  9b34b97 ckpt 460: M1-M3: live +EV findings in RESEARCH 21 (not feasible on free feeds; measured)
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
