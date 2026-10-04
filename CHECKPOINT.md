# CHECKPOINT 2519 — read me first, then TASKS.md

**Written:** 2026-10-04T04:17:48Z · **tests:** all 3 fast checks green
**Branch:** `ccr-55a7238c-jfgers` · **builds on:** `fb3eb962` (this checkpoint is the commit after it)

## Just done
BW5 prep: desk-level maker tests (held bets + bids kept across passes), MakerRules.of, race-refusal + no-book-read AutoBettor tests, legacy same-time games, blank-market bets; AutoBettor in-cycle exposure update removed (untestable duplicate; the placer re-reads the Tracker). Data 118 tests green

## Do this next
Run mutants in scratchpad/mutate.py in foreground batches (autosave would commit a mutated file if run in background), expect every one killed, fix any survivor with a stronger test; then full floor, review, version bump + ship

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6fea08cf ckpt 2518: BW4 done in code: maker (MakerRules.maxPerGame, RestingBid.game, MakerPlan h
  229fab48 ckpt 2517: BW4 step 2+3: ScanSettings.apiMaxPerGame (default 25, 0 = none) -> BetLimits
  a99026eb ckpt 2516: BW4 step 1: GameExposure (pure) + GameRef + TrackedBet.eventId (set by logAp
  38b2cca8 ckpt 2515: BW1 done (read-only): no per-game limit exists; game id missing on TrackedBe
  24a5e562 ckpt 2514: v0.58.4 released + recorded (release.yml run 37174473407 green, tag v0.58.4,
  9b32300f ckpt 2513: pre-release: v0.58.4: a close from another game can no longer count (a Washi
  2273ba90 ckpt 2512: BX3 log-only fixes done in data: rules line now says CNO's edge/odds/books, 
  3486e5f2 ckpt 2511: BX2 done in data: WSU close bug confirmed (0.5 matcher bar + latest row not 
  1a67a1d6 ckpt 2510: BX: Tj sent an outside analysis of the first scan-study export (v0.58.3, 622
  f39e0e5a ckpt 2509: BW: wrote Tj's per-game exposure request into TASKS.md (BW1-BW5)
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
