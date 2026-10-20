# CHECKPOINT 3803 — read me first, then TASKS.md

**Written:** 2026-10-20T04:32:19Z · **tests:** all 6 fast checks green
**Branch:** `claude/kalshi-vigilant-integration-v5u4ho` · **builds on:** `728af573a` (this checkpoint is the commit after it)

## Just done
KV10 docs: CLAUDE.md section, research note; KalshiMirror (APIs from Novig, money from Kalshi)

## Do this next
full test gate -> ship.sh; then KV9 engine lane

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
    M  CLAUDE.md
    A  research/kalshi_vigilant_2026-10-20.md

## Last ten checkpoints
```
  8196c29fb ckpt 3802: Kalshi bids in the hub + Bids page
  d18e71c32 ckpt 3801: KV4: KalshiAsNovig adapter + KalshiBidRunner on the unchanged MakerDesk; te
  fd0eefdb9 ckpt 3800: KV6/7: Kalshi tab (EV, arb, bets, auto, setup), venue switches, bet sheet; 
  2852455ed ckpt 3799: KV6 KalshiHub (scan, bets, arb, venues) compiles
  390e15c1d ckpt 3798: KV8 settler + tests
  e8e4aa6b2 ckpt 3797: KV5b: ArbExecutor + placeExact + KalshiConfig/VenueMode + BorrowedSource + 
  707c22718 ckpt 3796: KV3b: KalshiAutoBettor (rules reuse, paper mode) + tests green
  ed4eb9c91 ckpt 3795: KV5 finder done (KalshiArb + Planner.planOne alias) tests green
  49d87ec9a ckpt 3794: KV3 core: KalshiBook, KalshiBetPlacer (whole contracts, rounded fee, IOC, l
  edfc06832 ckpt 3793: KV1 tests green (8). ApiBetPlanner reuse plan for Kalshi placer
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
