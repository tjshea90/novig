# CHECKPOINT 3925 — read me first, then TASKS.md

**Written:** 2026-10-21T22:14:49Z · **tests:** all 9 fast checks green
**Branch:** `claude/new-session-51eqix` · **builds on:** `67901f983` (this checkpoint is the commit after it)

## Just done
v1.0.40 released (CK). Full test in progress: Kalshi CNO rows' alerts/taps open Kalshi (betLink), link lane skips Kalshi rows (mutation-checked), engine CNO hands reads to the phone at once when its first read fails, Settings switch Scan CNO on GitHub, stale engine copy, SX Bet chip name, detail sheet Open in Kalshi

## Do this next
continue full test sweep: adversarial review of v1.0.35-1.0.39 diffs (other sessions), then full floor, ship v1.0.41

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  d283653f0 ckpt 3924: pre-release: v1.0.40: CNO scans Novig and Kalshi (tab chips, Kalshi EV net 
  6916f0eaf ckpt 3923: CK2/CK3 built: CnoVenueSource (Novig+Kalshi reads, Kalshi EV net of fee), v
  038eed8e0 ckpt 3922: v1.0.39 released (BC3,BC4)
  e8d6f4aca ckpt 3921: pre-release: v1.0.39: engine closes BoltOdds/Kalshi/SX sockets cleanly on j
  9f8538b7f ckpt 3920: pre-ship: v1.0.39: engine closes BoltOdds/Kalshi/SX sockets cleanly on job 
  e971b4375 ckpt 3919: BC3 shutdown hook closes sockets, BC4 too-many-connections no longer refuse
  2295d6196 ckpt 3918: BC: phone already closes BoltOdds sockets on toggle-off; STOP ALL/pause pat
  906472197 ckpt 3917: BC task written (BoltOdds too many connections, clean disconnect on every s
  2d56888a0 ckpt 3916: pre-release: v1.0.38: API card trusts a fresh accepted key check over a sta
  e96c94d08 ckpt 3915: ApiState: a fresh accepted key check outranks a ledger 'every key refused' 
```

(10 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
