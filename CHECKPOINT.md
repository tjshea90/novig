# CHECKPOINT 3926 — read me first, then TASKS.md

**Written:** 2026-10-21T22:21:02Z · **tests:** all 9 fast checks green
**Branch:** `claude/new-session-51eqix` · **builds on:** `0f5b24e2d` (this checkpoint is the commit after it)

## Just done
full test (CK5/XO6) done: floor 4135 green, 134 screens viewed, 7 fixes (see TASKS CK5)

## Do this next
ship v1.0.41 and release; then the engine's first live CNO reads (status.cnoNote, engine-log) are the proof CK3 works on a runner

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  77c72f06e ckpt 3925: v1.0.40 released (CK). Full test in progress: Kalshi CNO rows' alerts/taps 
  d283653f0 ckpt 3924: pre-release: v1.0.40: CNO scans Novig and Kalshi (tab chips, Kalshi EV net 
  6916f0eaf ckpt 3923: CK2/CK3 built: CnoVenueSource (Novig+Kalshi reads, Kalshi EV net of fee), v
  038eed8e0 ckpt 3922: v1.0.39 released (BC3,BC4)
  e8d6f4aca ckpt 3921: pre-release: v1.0.39: engine closes BoltOdds/Kalshi/SX sockets cleanly on j
  9f8538b7f ckpt 3920: pre-ship: v1.0.39: engine closes BoltOdds/Kalshi/SX sockets cleanly on job 
  e971b4375 ckpt 3919: BC3 shutdown hook closes sockets, BC4 too-many-connections no longer refuse
  2295d6196 ckpt 3918: BC: phone already closes BoltOdds sockets on toggle-off; STOP ALL/pause pat
  906472197 ckpt 3917: BC task written (BoltOdds too many connections, clean disconnect on every s
  2d56888a0 ckpt 3916: pre-release: v1.0.38: API card trusts a fresh accepted key check over a sta
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
