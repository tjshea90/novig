# CHECKPOINT 3920 — read me first, then TASKS.md

**Written:** 2026-10-21T21:07:46Z · **tests:** all 9 fast checks green
**Branch:** `claude/optimistic-curie-gc6pie` · **builds on:** `e971b4375` (this checkpoint is the commit after it)

## Just done
pre-ship: v1.0.39: engine closes BoltOdds/Kalshi/SX sockets cleanly on job end or cancel; a 'too many concurrent connections' close no longer marks the BoltOdds key refused

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  e971b4375 ckpt 3919: BC3 shutdown hook closes sockets, BC4 too-many-connections no longer refuse
  2295d6196 ckpt 3918: BC: phone already closes BoltOdds sockets on toggle-off; STOP ALL/pause pat
  906472197 ckpt 3917: BC task written (BoltOdds too many connections, clean disconnect on every s
  2d56888a0 ckpt 3916: pre-release: v1.0.38: API card trusts a fresh accepted key check over a sta
  e96c94d08 ckpt 3915: ApiState: a fresh accepted key check outranks a ledger 'every key refused' 
  4c0826410 ckpt 3914: pre-release: v1.0.37: sharp-confirmed bids step their margin down to 1.5% u
  cb7b3025c ckpt 3913: pre-ship: v1.0.37: sharp-confirmed bids step their margin down to 1.5% unde
  edf8cc5e2 ckpt 3913: ANALYST 2026-10-21 skipped: under 100 new settled; engine healthy
  70c07be9a ckpt 3912: CH59 sharp bid margin step-down merged onto main line, tests green (2863), 
  f9837152a ckpt 3901: CH59 sharp-confirmed bid margin step-down to 1.5% (tests green, not release
```
