# CHECKPOINT 2518 — read me first, then TASKS.md

**Written:** 2026-10-04T04:15:33Z · **tests:** all 3 fast checks green
**Branch:** `ccr-55a7238c-jfgers` · **builds on:** `aa0f2959` (this checkpoint is the commit after it)

## Just done
BW4 done in code: maker (MakerRules.maxPerGame, RestingBid.game, MakerPlan heldItems + GAME_REACHED, desk wiring), recommendations skip bids past the cap, Settings > Betting & Novig account chip row (10/25/50/100/No limit) + wording, Diagnostics lines; tests: GameExposureTest 16, ApiBettingTest 43, AutoBettorTest 48, MakerTest 52, MakerAppTest 18, ApiBettingUiTest 34 all green

## Do this next
BW5: run mutants over the guard (script in scratchpad: apply one mutation, expect the named tests to FAIL, revert), then full floor bash tools/test.sh, code review, bump version to 0.59.0 (code 105) + BUILDLOG via ship.sh, release, answer Tj with the link and ask him to resend his diagnostics file (BW2)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M data/src/test/kotlin/com/tjshea/vigilant/data/tracker/GameExposureTest.kt

## Last ten checkpoints
```
  229fab48 ckpt 2517: BW4 step 2+3: ScanSettings.apiMaxPerGame (default 25, 0 = none) -> BetLimits
  a99026eb ckpt 2516: BW4 step 1: GameExposure (pure) + GameRef + TrackedBet.eventId (set by logAp
  38b2cca8 ckpt 2515: BW1 done (read-only): no per-game limit exists; game id missing on TrackedBe
  24a5e562 ckpt 2514: v0.58.4 released + recorded (release.yml run 37174473407 green, tag v0.58.4,
  9b32300f ckpt 2513: pre-release: v0.58.4: a close from another game can no longer count (a Washi
  2273ba90 ckpt 2512: BX3 log-only fixes done in data: rules line now says CNO's edge/odds/books, 
  3486e5f2 ckpt 2511: BX2 done in data: WSU close bug confirmed (0.5 matcher bar + latest row not 
  1a67a1d6 ckpt 2510: BX: Tj sent an outside analysis of the first scan-study export (v0.58.3, 622
  f39e0e5a ckpt 2509: BW: wrote Tj's per-game exposure request into TASKS.md (BW1-BW5)
  df2c9ade ckpt 2508: v0.58.3 released + recorded (release.yml run 37170946701 green, tag v0.58.3)
```

(8 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
