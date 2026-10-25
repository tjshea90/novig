# CHECKPOINT 4256 — read me first, then TASKS.md

**Written:** 2026-10-25T23:31:49Z · **tests:** all 9 fast checks green
**Branch:** `ccr-3cb0aa53-b2tjli` · **builds on:** `ba798dc20` (this checkpoint is the commit after it)

## Just done
PX11f root cause + fix: ParlayAPI's split events (two spellings of one game) now both match; PlannerPricingTest

## Do this next
full tools/test.sh, land on main, engine reset (engine_set.py --reset), check feed row Northwestern@Oregon has parlay books; tell Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  44717fce0 ckpt 4255: PX11f: gameprobe lists ParlayAPI's events of one game (hypothesis: ParlayAP
  13eb830d5 ckpt 4254: WATCH 2026-10-25 ok, no changes
  66d520be3 ckpt 4253: v1.0.85 released + recorded; engine reset onto the order-port fix
  0e6753355 ckpt 4252: pre-release: v1.0.85 PX11: a filled order whose fills Novig would not let u
  caa38346a ckpt 4251: pre-ship: v1.0.85 PX11: a filled order whose fills Novig would not let us r
  28e1d9889 ckpt 4250: PX11a/b/c done in code (fill misreport fixed phone+engine, NOFILL keeps the
  694adcf71 ckpt 4249: PX11c bid study (keep the sharp veto: 0 fills against a sharp book, no-shar
  5f0740331 ckpt 4248: PX11a root cause + fix (FILLED orders whose fills were rate-limited were re
  6a646d461 ckpt 4247: PX11a: novig-probe.yml passes default statuses (Gradle refuses empty --args
  3e9157ea2 ckpt 4246: PX11a: novig-probe.yml + cloud/OrdersProbeMain.kt (Novig's record of reject
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
