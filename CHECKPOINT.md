# CHECKPOINT 3534 — read me first, then TASKS.md

**Written:** 2026-10-17T06:59:32Z · **tests:** all 5 fast checks green
**Branch:** `ccr-b02146f9-09npa9` · **builds on:** `a42752d44` (this checkpoint is the commit after it)

## Just done
pre-release: v0.114.17: the app can't show stale data (phone-link, engine quiet/stalled, old bets list), 2 s status polling, an engine switched off resumes at the next 8am ET opening, Stop lasts until then, engine loop watchdog (versionCode 313, v0.114.17)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.114.17), then run: bash tools/record-release.sh v0.114.17 313 "v0.114.17: the app can't show stale data (phone-link, engine quiet/stalled, old bets list), 2 s status polling, an engine switched off resumes at the next 8am ET opening, Stop lasts until then, engine loop watchdog"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  a42752d44 ckpt 3533: pre-ship: v0.114.17: the app can't show stale data (phone-link, engine quie
  618ba64c7 ckpt 3532: v0.114.17: stale-data rules (phone link, quiet/stalled, old bets), 2 s poll
  f15691cba ckpt 3531: TCL81 auto-resume of a hand-off engine at next opening, loop watchdog w/ st
  d2b467b17 ckpt 3530: progressChangedAtMs status field + watchdog stall rule
  44c2f9b40 ckpt 3529: pre-release: v0.114.16: Engine tab Address tile shows the real Novig verdic
  901b20b97 ckpt 3528: v0.114.16: Address tile reads the real verdict, novigAddress status, Addres
  eb1c8c25c ckpt 3527: TCL78: standing job rule at top of CLAUDE.md + ROUTINE.md; address tile fix
  8d7e224a6 ckpt 3526: pre-release: v0.114.15: Run now / Stop now for the GitHub engine from Setti
  5bda9c5ec ckpt 3525: TCL74: manual Run/Stop of the engine outside hours (CloudManual, config fie
  ccd7c6029 ckpt 3524: pre-release: v0.114.14: red STAY AWAY banner on bet types that lose in your
```
