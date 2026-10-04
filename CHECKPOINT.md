# CHECKPOINT 2516 — read me first, then TASKS.md

**Written:** 2026-10-04T04:02:52Z · **tests:** all 3 fast checks green
**Branch:** `ccr-55a7238c-jfgers` · **builds on:** `bff5a6e6` (this checkpoint is the commit after it)

## Just done
BW4 step 1: GameExposure (pure) + GameRef + TrackedBet.eventId (set by logApi, maker fills, opportunityBet) built test-first; GameExposureTest 15 green (one game = Novig event, legacy bets fall back to matchup+12h/MLB 2h, per market larger side, locked markets and locks count 0, a bet that adds no risk is never blocked, cap 0 = none)

## Do this next
Step 2: ScanSettings.maxPerGame (default 25) + BetLimits.maxPerGame; ApiBetPlanner.plan takes the game check (auto refuses, manual warns in plan.note); ApiBetPlacer.plan builds it from tracker.all() + resting bids; tests in ApiBettingTest

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  38b2cca8 ckpt 2515: BW1 done (read-only): no per-game limit exists; game id missing on TrackedBe
  24a5e562 ckpt 2514: v0.58.4 released + recorded (release.yml run 37174473407 green, tag v0.58.4,
  9b32300f ckpt 2513: pre-release: v0.58.4: a close from another game can no longer count (a Washi
  2273ba90 ckpt 2512: BX3 log-only fixes done in data: rules line now says CNO's edge/odds/books, 
  3486e5f2 ckpt 2511: BX2 done in data: WSU close bug confirmed (0.5 matcher bar + latest row not 
  1a67a1d6 ckpt 2510: BX: Tj sent an outside analysis of the first scan-study export (v0.58.3, 622
  f39e0e5a ckpt 2509: BW: wrote Tj's per-game exposure request into TASKS.md (BW1-BW5)
  df2c9ade ckpt 2508: v0.58.3 released + recorded (release.yml run 37170946701 green, tag v0.58.3)
  d0334f7d ckpt 2507: pre-release: v0.58.3: bids are kept within the wallet and the day's limit (a
  c80df9fc ckpt 2506: pre-ship: v0.58.3: bids are kept within the wallet and the day's limit (a be
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
