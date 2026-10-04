# CHECKPOINT 2521 — read me first, then TASKS.md

**Written:** 2026-10-04T05:10:07Z · **tests:** all 3 fast checks green
**Branch:** `ccr-55a7238c-jfgers` · **builds on:** `219a90cf` (this checkpoint is the commit after it)

## Just done
v0.59.0 released + recorded (release.yml run 37178804663 green, tag v0.59.0, CI green on the shipped commit): per-game exposure limit BW1-BW5 done (BW2 open: needs Tj's diagnostics file to tune the $25 default)

## Do this next
Nothing in flight. Open: BW2 (Tj to resend his diagnostics file with the EVERY BET lines; then measure same-game clusters and tune apiMaxPerGame), BX5 (ask Tj: read a book page for every prop >= 1.5% EV; re-export the scan study after Sunday's NFL games). Optional: Tj's next scan-study file: CLV by available-dollars bucket (his liquidity question)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  0ac10a65 ckpt 2520: pre-release: v0.59.0: most at risk on one game (Settings › Betting & Novig
  519ec408 ckpt 2519: BW5 prep: desk-level maker tests (held bets + bids kept across passes), Make
  6fea08cf ckpt 2518: BW4 done in code: maker (MakerRules.maxPerGame, RestingBid.game, MakerPlan h
  229fab48 ckpt 2517: BW4 step 2+3: ScanSettings.apiMaxPerGame (default 25, 0 = none) -> BetLimits
  a99026eb ckpt 2516: BW4 step 1: GameExposure (pure) + GameRef + TrackedBet.eventId (set by logAp
  38b2cca8 ckpt 2515: BW1 done (read-only): no per-game limit exists; game id missing on TrackedBe
  24a5e562 ckpt 2514: v0.58.4 released + recorded (release.yml run 37174473407 green, tag v0.58.4,
  9b32300f ckpt 2513: pre-release: v0.58.4: a close from another game can no longer count (a Washi
  2273ba90 ckpt 2512: BX3 log-only fixes done in data: rules line now says CNO's edge/odds/books, 
  3486e5f2 ckpt 2511: BX2 done in data: WSU close bug confirmed (0.5 matcher bar + latest row not 
```
