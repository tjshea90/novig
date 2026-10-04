# CHECKPOINT 2517 — read me first, then TASKS.md

**Written:** 2026-10-04T04:09:50Z · **tests:** all 3 fast checks green
**Branch:** `ccr-55a7238c-jfgers` · **builds on:** `c293e3c9` (this checkpoint is the commit after it)

## Just done
BW4 step 2+3: ScanSettings.apiMaxPerGame (default 25, 0 = none) -> BetLimits.maxPerGame; ApiBetPlanner game check (auto refuses with gameLimit flag, hand bet warned in plan.note); ApiBetPlacer builds it from tracker + resting bids (restingBids lambda wired in VigilantApp + ApiBetting); AutoBettor pre-filter (stable skip reason AutoBet.GAME_LIMIT_SKIP, one log line per hold, exposure updated per placed bet). ApiBettingTest 43 + AutoBettorTest 48 green

## Do this next
Step 4: maker: MakerPlan/MakerDesk must keep auto-make and hand-posted bids inside the per-game cap (RestingBid gets its game; kept bids + open bets count; waiting reason GAME_REACHED); then UI chip row (Auto-bet tab + Settings) + Diagnostics settings line, then BW5 mutants/full floor/review/ship

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  a99026eb ckpt 2516: BW4 step 1: GameExposure (pure) + GameRef + TrackedBet.eventId (set by logAp
  38b2cca8 ckpt 2515: BW1 done (read-only): no per-game limit exists; game id missing on TrackedBe
  24a5e562 ckpt 2514: v0.58.4 released + recorded (release.yml run 37174473407 green, tag v0.58.4,
  9b32300f ckpt 2513: pre-release: v0.58.4: a close from another game can no longer count (a Washi
  2273ba90 ckpt 2512: BX3 log-only fixes done in data: rules line now says CNO's edge/odds/books, 
  3486e5f2 ckpt 2511: BX2 done in data: WSU close bug confirmed (0.5 matcher bar + latest row not 
  1a67a1d6 ckpt 2510: BX: Tj sent an outside analysis of the first scan-study export (v0.58.3, 622
  f39e0e5a ckpt 2509: BW: wrote Tj's per-game exposure request into TASKS.md (BW1-BW5)
  df2c9ade ckpt 2508: v0.58.3 released + recorded (release.yml run 37170946701 green, tag v0.58.3)
  d0334f7d ckpt 2507: pre-release: v0.58.3: bids are kept within the wallet and the day's limit (a
```

(13 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
