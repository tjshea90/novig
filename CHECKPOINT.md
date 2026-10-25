# CHECKPOINT 4261 — read me first, then TASKS.md

**Written:** 2026-10-25T23:53:19Z · **tests:** all 9 fast checks green
**Branch:** `ccr-3cb0aa53-b2tjli` · **builds on:** `3c90f1061` (this checkpoint is the commit after it)

## Just done
pre-ship: v1.0.86 PR1 + PX11: Engine pull to refresh reads only the engine's data, Engine > APIs has its own Refresh API status and meters button; ParlayAPI's second listing of a game (team names spelled two ways) now prices the line, so its US books' fresh quotes are no longer lost; bids never ask fewer than 3 agreeing books; per-book outlier counts

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  3bf49b42a ckpt 4260: PR1a/b: Engine pull refreshes engine data only; Engine › APIs has its own
  4c8fb2f43 ckpt 4259: research index rebuilt; engine reset onto main (PX11f fix)
  76fc04638 ckpt 4258: PX11f/c/d: ParlayAPI split-event fix, bid 3-book floor, per-book outlier ce
  088c27c54 ckpt 4257: PX11c: bid slider never asks fewer than 3 agreeing books (BidSlider.MIN_AGR
  82a2aad46 ckpt 4256: PX11f root cause + fix: ParlayAPI's split events (two spellings of one game
  44717fce0 ckpt 4255: PX11f: gameprobe lists ParlayAPI's events of one game (hypothesis: ParlayAP
  13eb830d5 ckpt 4254: WATCH 2026-10-25 ok, no changes
  66d520be3 ckpt 4253: v1.0.85 released + recorded; engine reset onto the order-port fix
  0e6753355 ckpt 4252: pre-release: v1.0.85 PX11: a filled order whose fills Novig would not let u
  caa38346a ckpt 4251: pre-ship: v1.0.85 PX11: a filled order whose fills Novig would not let us r
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
