# CHECKPOINT 3801 — read me first, then TASKS.md

**Written:** 2026-10-20T04:27:56Z · **tests:** all 6 fast checks green
**Branch:** `claude/kalshi-vigilant-integration-v5u4ho` · **builds on:** `14ea836f9` (this checkpoint is the commit after it)

## Just done
KV4: KalshiAsNovig adapter + KalshiBidRunner on the unchanged MakerDesk; tests green

## Do this next
hub: bids wiring + Bids page; then full test gate, engine lane KV9, docs KV10

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  fd0eefdb9 ckpt 3800: KV6/7: Kalshi tab (EV, arb, bets, auto, setup), venue switches, bet sheet; 
  2852455ed ckpt 3799: KV6 KalshiHub (scan, bets, arb, venues) compiles
  390e15c1d ckpt 3798: KV8 settler + tests
  e8e4aa6b2 ckpt 3797: KV5b: ArbExecutor + placeExact + KalshiConfig/VenueMode + BorrowedSource + 
  707c22718 ckpt 3796: KV3b: KalshiAutoBettor (rules reuse, paper mode) + tests green
  ed4eb9c91 ckpt 3795: KV5 finder done (KalshiArb + Planner.planOne alias) tests green
  49d87ec9a ckpt 3794: KV3 core: KalshiBook, KalshiBetPlacer (whole contracts, rounded fee, IOC, l
  edfc06832 ckpt 3793: KV1 tests green (8). ApiBetPlanner reuse plan for Kalshi placer
  da67547f2 ckpt 3792: KV1: KalshiTradingClient (V2 orders w/ legacy fallback, cancel, balance/fil
  a8f57e056 ckpt 3791: KV2 started: venue read in KalshiClient, BookBoard/SportsbookScanner seams,
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
