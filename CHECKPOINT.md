# CHECKPOINT 474 — read me first, then TASKS.md

**Written:** 2026-09-27T03:25:34Z · **tests:** all 1 fast checks green
**Branch:** `claude/cno-scanner-enhancements-tj52i1` · **builds on:** `20874d9` (this checkpoint is the commit after it)

## Just done
pre-release: v0.15.6: every bet you check (widget, CNO tab) is logged in the Tracker for good ($1, editable), earlier checks moved in; results settle from Novig automatically (app open, Tracker tab, every 3 h in the background); Check odds now shows each open bet's EV now in green/red; new Stats view: win %, record, profit and profit % (green/red), running-profit line, by scanner and period. 496 tests (versionCode 26, v0.15.6)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.15.6), then run: bash tools/record-release.sh v0.15.6 26 "v0.15.6: every bet you check (widget, CNO tab) is logged in the Tracker for good ($1, editable), earlier checks moved in; results settle from Novig automatically (app open, Tracker tab, every 3 h in the background); Check odds now shows each open bet's EV now in green/red; new Stats view: win %, record, profit and profit % (green/red), running-profit line, by scanner and period. 496 tests"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  a5a400c ckpt 473: N6 full tests: review of N1-N5 code + callers, live Novig check (public list h
  426cb05 ckpt 472: N1-N5 done and ticked (TASKS.md names tests); one-time import flag (tracker_im
  7bdcde3 ckpt 471: N5 UI written: TrackerScreen Stats|Bets switch, period chips (inPeriod), profi
  3d53a57 ckpt 470: N4 data+VM done: BetRecheck (CNO game page -> CnoBooks.check fair -> nowFair/n
  3db6c81 ckpt 469: N2 wired: container.settler (novig.market + betFinder.findEnded), SettleWorker
  fb3be0b ckpt 468: N2 data done: BetSettler (Novig outcome WIN/LOSS/PUSH/FMV -> tracked result, 1
  9eb8506 ckpt 467: N1 VM wiring done: markPlaced logs every check (Vigilant item -> tracker.track
  081658e ckpt 466: N1 data layer done: BetTracker.logCno/untrack/edit/setStake/importPlaced + tes
  94081ed ckpt 465: v0.15.5 released+recorded (link sent). N1 step 1 done: TrackedBet model extend
  e00442e ckpt 464: N-plan: full design for tracker/stats written into TASKS.md (N3 answered: not 
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
