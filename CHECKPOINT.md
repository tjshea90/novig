# CHECKPOINT 3555 — read me first, then TASKS.md

**Written:** 2026-10-17T17:10:15Z · **tests:** all 5 fast checks green
**Branch:** `claude/keen-keller-8fzr9r` · **builds on:** `078e3da9d` (this checkpoint is the commit after it)

## Just done
pre-release: v0.114.19: Bet button on every PropLine +EV row (lines Vigilant never priced use PropLine's fair, prop cut, Kelly stake); engine mid-scan re-read no longer waits on the scan, watchdog no longer restarts healthy jobs (versionCode 315, v0.114.19)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.114.19), then run: bash tools/record-release.sh v0.114.19 315 "v0.114.19: Bet button on every PropLine +EV row (lines Vigilant never priced use PropLine's fair, prop cut, Kelly stake); engine mid-scan re-read no longer waits on the scan, watchdog no longer restarts healthy jobs"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  078e3da9d ckpt 3554: pre-ship: v0.114.19: Bet button on every PropLine +EV row (lines Vigilant n
  cec23e7a9 ckpt 3553: TCL88a: Bet button on PropLine +EV rows Vigilant never priced (PropLine fai
  2a364e11c ckpt 3552: TCL87b decided: no trim while scans are ~4 min
  1fc8ed937 ckpt 3551: TCL87a verified: engine runs >15 min
  8b5a5d11d ckpt 3550: TCL87a2: mid-scan re-read before judging uses requoteListed (recheck waited
  d7eac8f5a ckpt 3549: TCL87a: watchdog false positive fixed (bounded watch steps 60s; log activit
  c46f496bf ckpt 3548: TCL84a 5xx watch, TCL86b chat fallback chain, TCL86c auto account label; ch
  aec5f87fe ckpt 3547: TCL84a: NovigErrorWatch (5xx short retry + alert/recovered messages) wired 
  37e1b462e ckpt 3546: v0.114.18 released (reset engine, refresh, stall watchdog, 8-min engine bid
  3844f9787 ckpt 3545: pre-release: v0.114.18: Reset engine, pull-to-refresh Engine tab, frozen No
```
