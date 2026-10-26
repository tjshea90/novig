# CHECKPOINT 4265 — read me first, then TASKS.md

**Written:** 2026-10-26T00:12:38Z · **tests:** all 9 fast checks green
**Branch:** `ccr-3cb0aa53-b2tjli` · **builds on:** `3355ea94d` (this checkpoint is the commit after it)

## Just done
PX12 landed on main, engine reset; PX12c answered (no socket contention)

## Do this next
after the new run's scans: far-row exchange ages <= ~3 min? near fairAgeMedianS? 'book outliers' line; then tell Tj with v1.0.86 link

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  29d2da2d3 ckpt 4264: PX12: full scan every 2 min, not 6 (far games' fair was 295 s old on every 
  84ec1b9df ckpt 4263: PX11d-2: census line 'book outliers' = each book's share left out by BadOdd
  51935d90a ckpt 4262: pre-release: v1.0.86 PR1 + PX11: Engine pull to refresh reads only the engi
  c3a33ddfc ckpt 4261: pre-ship: v1.0.86 PR1 + PX11: Engine pull to refresh reads only the engine'
  3bf49b42a ckpt 4260: PR1a/b: Engine pull refreshes engine data only; Engine › APIs has its own
  4c8fb2f43 ckpt 4259: research index rebuilt; engine reset onto main (PX11f fix)
  76fc04638 ckpt 4258: PX11f/c/d: ParlayAPI split-event fix, bid 3-book floor, per-book outlier ce
  088c27c54 ckpt 4257: PX11c: bid slider never asks fewer than 3 agreeing books (BidSlider.MIN_AGR
  82a2aad46 ckpt 4256: PX11f root cause + fix: ParlayAPI's split events (two spellings of one game
  44717fce0 ckpt 4255: PX11f: gameprobe lists ParlayAPI's events of one game (hypothesis: ParlayAP
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
