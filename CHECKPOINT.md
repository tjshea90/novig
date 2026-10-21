# CHECKPOINT 3924 — read me first, then TASKS.md

**Written:** 2026-10-21T22:02:45Z · **tests:** all 9 fast checks green
**Branch:** `claude/new-session-51eqix` · **builds on:** `264845c10` (this checkpoint is the commit after it)

## Just done
pre-release: v1.0.40: CNO scans Novig and Kalshi (tab chips, Kalshi EV net of fee, venue card colours); the whole CNO scan runs on the GitHub engine (cno.json.gz), phone adopts it (versionCode 380, v1.0.40)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.40), then run: bash tools/record-release.sh v1.0.40 380 "v1.0.40: CNO scans Novig and Kalshi (tab chips, Kalshi EV net of fee, venue card colours); the whole CNO scan runs on the GitHub engine (cno.json.gz), phone adopts it"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6916f0eaf ckpt 3923: CK2/CK3 built: CnoVenueSource (Novig+Kalshi reads, Kalshi EV net of fee), v
  038eed8e0 ckpt 3922: v1.0.39 released (BC3,BC4)
  e8d6f4aca ckpt 3921: pre-release: v1.0.39: engine closes BoltOdds/Kalshi/SX sockets cleanly on j
  9f8538b7f ckpt 3920: pre-ship: v1.0.39: engine closes BoltOdds/Kalshi/SX sockets cleanly on job 
  e971b4375 ckpt 3919: BC3 shutdown hook closes sockets, BC4 too-many-connections no longer refuse
  2295d6196 ckpt 3918: BC: phone already closes BoltOdds sockets on toggle-off; STOP ALL/pause pat
  906472197 ckpt 3917: BC task written (BoltOdds too many connections, clean disconnect on every s
  2d56888a0 ckpt 3916: pre-release: v1.0.38: API card trusts a fresh accepted key check over a sta
  e96c94d08 ckpt 3915: ApiState: a fresh accepted key check outranks a ledger 'every key refused' 
  4c0826410 ckpt 3914: pre-release: v1.0.37: sharp-confirmed bids step their margin down to 1.5% u
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
