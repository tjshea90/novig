# CHECKPOINT 4260 — read me first, then TASKS.md

**Written:** 2026-10-25T23:52:42Z · **tests:** all 9 fast checks green
**Branch:** `ccr-3cb0aa53-b2tjli` · **builds on:** `68e53ecf7` (this checkpoint is the commit after it)

## Just done
PR1a/b: Engine pull refreshes engine data only; Engine › APIs has its own Refresh API status and meters button (EngineRefreshUiTest)

## Do this next
ship v1.0.86 (PR1 + PX11 phone-side), release.yml, record, tell Tj; check engine run 40245944083 for parlay books on Northwestern@Oregon

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  4c8fb2f43 ckpt 4259: research index rebuilt; engine reset onto main (PX11f fix)
  76fc04638 ckpt 4258: PX11f/c/d: ParlayAPI split-event fix, bid 3-book floor, per-book outlier ce
  088c27c54 ckpt 4257: PX11c: bid slider never asks fewer than 3 agreeing books (BidSlider.MIN_AGR
  82a2aad46 ckpt 4256: PX11f root cause + fix: ParlayAPI's split events (two spellings of one game
  44717fce0 ckpt 4255: PX11f: gameprobe lists ParlayAPI's events of one game (hypothesis: ParlayAP
  13eb830d5 ckpt 4254: WATCH 2026-10-25 ok, no changes
  66d520be3 ckpt 4253: v1.0.85 released + recorded; engine reset onto the order-port fix
  0e6753355 ckpt 4252: pre-release: v1.0.85 PX11: a filled order whose fills Novig would not let u
  caa38346a ckpt 4251: pre-ship: v1.0.85 PX11: a filled order whose fills Novig would not let us r
  28e1d9889 ckpt 4250: PX11a/b/c done in code (fill misreport fixed phone+engine, NOFILL keeps the
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
