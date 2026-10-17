# CHECKPOINT 3544 — read me first, then TASKS.md

**Written:** 2026-10-17T15:55:30Z · **tests:** all 5 fast checks green
**Branch:** `ccr-28335dd4-j22axd` · **builds on:** `0dd3e402e` (this checkpoint is the commit after it)

## Just done
chat kind lease (coord.py --kind chat), engine bid TTL cap, reset/refresh, progress watchdog, update check; v0.114.18 gate running

## Do this next
wait for ship.sh gate, trigger release.yml, record-release, send Tj link; TCL84a alert, TCL86b/c remain

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  98f1ab7b2 ckpt 3543: pre-ship: v0.114.18: Reset engine button, pull-to-refresh on Engine tab, fr
  8d462987a ckpt 3542: chat: TCL86 chat-wake should build
  e05e2d5a5 ckpt 3541: TCL81b: engine resumed 08:00 ET (dispatched), address accepted, tunnel live
  eefce2003 ckpt 3540: WATCH 2026-10-17 routine: engine idle pre-window, digest unchanged, nothing
  419d9da5d ckpt 3539: v0.114.17 released (tag created server-side by release action after tag pus
  de89ccc0a ckpt 3538: pre-release: v0.114.17: the app can't show stale data (phone link, engine q
  f50a76f8d ckpt 3537: pre-ship: v0.114.17: the app can't show stale data (phone link, engine quie
  381640bde ckpt 3536: TCL69 origin dataset + digest table; TCL80 live tunnel code
  e8a954b21 ckpt 3535: TCL80 live tunnel: engine status server, tunnel step, phone live reader + c
  eca75c18a ckpt 3534: pre-release: v0.114.17: the app can't show stale data (phone-link, engine q
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
