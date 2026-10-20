# CHECKPOINT 3805 — read me first, then TASKS.md

**Written:** 2026-10-20T04:39:21Z · **tests:** all 6 fast checks green
**Branch:** `claude/kalshi-vigilant-integration-v5u4ho` · **builds on:** `3542fc902` (this checkpoint is the commit after it)

## Just done
KV9 phone side: GitHub engine card, kalshi.json push, status poll; lane honors STOP ALL and the orders switch

## Do this next
full test then ship

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
    M  data/src/main/kotlin/com/tjshea/vigilant/data/cloud/CloudKalshiLane.kt

## Last ten checkpoints
```
  ea4ae6d69 ckpt 3804: KV9 engine lane: CloudKalshiLane + models + CloudMain wiring (additive) + t
  4764eb008 ckpt 3797: AS tasks written: API status lights lie (pinnacle feed id shared by PinnWir
  0ec1b5fac ckpt 3803: KV10 docs: CLAUDE.md section, research note; KalshiMirror (APIs from Novig,
  8196c29fb ckpt 3802: Kalshi bids in the hub + Bids page
  d18e71c32 ckpt 3801: KV4: KalshiAsNovig adapter + KalshiBidRunner on the unchanged MakerDesk; te
  fd0eefdb9 ckpt 3800: KV6/7: Kalshi tab (EV, arb, bets, auto, setup), venue switches, bet sheet; 
  2852455ed ckpt 3799: KV6 KalshiHub (scan, bets, arb, venues) compiles
  390e15c1d ckpt 3798: KV8 settler + tests
  e8e4aa6b2 ckpt 3797: KV5b: ArbExecutor + placeExact + KalshiConfig/VenueMode + BorrowedSource + 
  707c22718 ckpt 3796: KV3b: KalshiAutoBettor (rules reuse, paper mode) + tests green
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
