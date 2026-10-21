# CHECKPOINT 3927 — read me first, then TASKS.md

**Written:** 2026-10-21T22:22:18Z · **tests:** all 9 fast checks green
**Branch:** `claude/new-session-51eqix` · **builds on:** `8047b101b` (this checkpoint is the commit after it)

## Just done
pre-release: v1.0.41: full test fixes: Kalshi CNO rows open at Kalshi everywhere, Scan CNO on GitHub switch, faster phone fallback when CNO refuses the runner, bid step-down needs every fair book fresh, copy fixes (versionCode 381, v1.0.41)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.41), then run: bash tools/record-release.sh v1.0.41 381 "v1.0.41: full test fixes: Kalshi CNO rows open at Kalshi everywhere, Scan CNO on GitHub switch, faster phone fallback when CNO refuses the runner, bid step-down needs every fair book fresh, copy fixes"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  8047b101b ckpt 3926: full test (CK5/XO6) done: floor 4135 green, 134 screens viewed, 7 fixes (se
  77c72f06e ckpt 3925: v1.0.40 released (CK). Full test in progress: Kalshi CNO rows' alerts/taps 
  d283653f0 ckpt 3924: pre-release: v1.0.40: CNO scans Novig and Kalshi (tab chips, Kalshi EV net 
  6916f0eaf ckpt 3923: CK2/CK3 built: CnoVenueSource (Novig+Kalshi reads, Kalshi EV net of fee), v
  038eed8e0 ckpt 3922: v1.0.39 released (BC3,BC4)
  e8d6f4aca ckpt 3921: pre-release: v1.0.39: engine closes BoltOdds/Kalshi/SX sockets cleanly on j
  9f8538b7f ckpt 3920: pre-ship: v1.0.39: engine closes BoltOdds/Kalshi/SX sockets cleanly on job 
  e971b4375 ckpt 3919: BC3 shutdown hook closes sockets, BC4 too-many-connections no longer refuse
  2295d6196 ckpt 3918: BC: phone already closes BoltOdds sockets on toggle-off; STOP ALL/pause pat
  906472197 ckpt 3917: BC task written (BoltOdds too many connections, clean disconnect on every s
```
