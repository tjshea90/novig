# CHECKPOINT 3548 — read me first, then TASKS.md

**Written:** 2026-10-17T16:09:04Z · **tests:** all 5 fast checks green
**Branch:** `ccr-28335dd4-j22axd` · **builds on:** `fea975d36` (this checkpoint is the commit after it)

## Just done
TCL84a 5xx watch, TCL86b chat fallback chain, TCL86c auto account label; chat-wake timeout 8 min on cloud-config

## Do this next
watch CI on main; engine picks NovigErrorWatch up at the next job start; Tj pastes routine prompt with --kind chat

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M research/coordination/CHAT_WAKE.md

## Last ten checkpoints
```
  aec5f87fe ckpt 3547: TCL84a: NovigErrorWatch (5xx short retry + alert/recovered messages) wired 
  37e1b462e ckpt 3546: v0.114.18 released (reset engine, refresh, stall watchdog, 8-min engine bid
  3844f9787 ckpt 3545: pre-release: v0.114.18: Reset engine, pull-to-refresh Engine tab, frozen No
  63882ac71 ckpt 3544: chat kind lease (coord.py --kind chat), engine bid TTL cap, reset/refresh, 
  98f1ab7b2 ckpt 3543: pre-ship: v0.114.18: Reset engine button, pull-to-refresh on Engine tab, fr
  8d462987a ckpt 3542: chat: TCL86 chat-wake should build
  e05e2d5a5 ckpt 3541: TCL81b: engine resumed 08:00 ET (dispatched), address accepted, tunnel live
  eefce2003 ckpt 3540: WATCH 2026-10-17 routine: engine idle pre-window, digest unchanged, nothing
  419d9da5d ckpt 3539: v0.114.17 released (tag created server-side by release action after tag pus
  de89ccc0a ckpt 3538: pre-release: v0.114.17: the app can't show stale data (phone link, engine q
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
