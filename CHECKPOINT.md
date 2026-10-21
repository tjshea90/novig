# CHECKPOINT 3923 — read me first, then TASKS.md

**Written:** 2026-10-21T21:49:37Z · **tests:** all 9 fast checks green
**Branch:** `claude/new-session-51eqix` · **builds on:** `773e4aa25` (this checkpoint is the commit after it)

## Just done
CK2/CK3 built: CnoVenueSource (Novig+Kalshi reads, Kalshi EV net of fee), venue chips + venue card colours on the CNO tab, Kalshi rows open at Kalshi and are skipped by Novig bids; engine CNO loop publishes cno.json.gz, phone adopts it (SwitchingCnoSource, falls back to its own reads when the engine does not serve); CnoVenuesTest, CloudCnoTest green

## Do this next
CK UI test for venue chips, full floor, ship v1.0.40, then CK5 full test protocol

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  038eed8e0 ckpt 3922: v1.0.39 released (BC3,BC4)
  e8d6f4aca ckpt 3921: pre-release: v1.0.39: engine closes BoltOdds/Kalshi/SX sockets cleanly on j
  9f8538b7f ckpt 3920: pre-ship: v1.0.39: engine closes BoltOdds/Kalshi/SX sockets cleanly on job 
  e971b4375 ckpt 3919: BC3 shutdown hook closes sockets, BC4 too-many-connections no longer refuse
  2295d6196 ckpt 3918: BC: phone already closes BoltOdds sockets on toggle-off; STOP ALL/pause pat
  906472197 ckpt 3917: BC task written (BoltOdds too many connections, clean disconnect on every s
  2d56888a0 ckpt 3916: pre-release: v1.0.38: API card trusts a fresh accepted key check over a sta
  e96c94d08 ckpt 3915: ApiState: a fresh accepted key check outranks a ledger 'every key refused' 
  4c0826410 ckpt 3914: pre-release: v1.0.37: sharp-confirmed bids step their margin down to 1.5% u
  cb7b3025c ckpt 3913: pre-ship: v1.0.37: sharp-confirmed bids step their margin down to 1.5% unde
```

(18 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
