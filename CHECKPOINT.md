# CHECKPOINT 2520 — read me first, then TASKS.md

**Written:** 2026-10-04T04:56:18Z · **tests:** all 3 fast checks green
**Branch:** `ccr-55a7238c-jfgers` · **builds on:** `cff0bcd4` (this checkpoint is the commit after it)

## Just done
pre-release: v0.59.0: most at risk on one game (Settings › Betting & Novig account, $25 by default, or no limit): every line, prop and bid of one game counts as one risk, so the auto-bet and auto-make never take a game past it (one team at +5, +6 and +10 is one bet that can lose three times); a bet by hand is only warned, a lock or the other side of a market you hold is never held back; bids you approve by hand stay your call; recommendations stop suggesting bids past it; each bet now keeps Novig's game id; Diagnostics shows the limit (versionCode 105, v0.59.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.59.0), then run: bash tools/record-release.sh v0.59.0 105 "v0.59.0: most at risk on one game (Settings › Betting & Novig account, $25 by default, or no limit): every line, prop and bid of one game counts as one risk, so the auto-bet and auto-make never take a game past it (one team at +5, +6 and +10 is one bet that can lose three times); a bet by hand is only warned, a lock or the other side of a market you hold is never held back; bids you approve by hand stay your call; recommendations stop suggesting bids past it; each bet now keeps Novig's game id; Diagnostics shows the limit"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  519ec408 ckpt 2519: BW5 prep: desk-level maker tests (held bets + bids kept across passes), Make
  6fea08cf ckpt 2518: BW4 done in code: maker (MakerRules.maxPerGame, RestingBid.game, MakerPlan h
  229fab48 ckpt 2517: BW4 step 2+3: ScanSettings.apiMaxPerGame (default 25, 0 = none) -> BetLimits
  a99026eb ckpt 2516: BW4 step 1: GameExposure (pure) + GameRef + TrackedBet.eventId (set by logAp
  38b2cca8 ckpt 2515: BW1 done (read-only): no per-game limit exists; game id missing on TrackedBe
  24a5e562 ckpt 2514: v0.58.4 released + recorded (release.yml run 37174473407 green, tag v0.58.4,
  9b32300f ckpt 2513: pre-release: v0.58.4: a close from another game can no longer count (a Washi
  2273ba90 ckpt 2512: BX3 log-only fixes done in data: rules line now says CNO's edge/odds/books, 
  3486e5f2 ckpt 2511: BX2 done in data: WSU close bug confirmed (0.5 matcher bar + latest row not 
  1a67a1d6 ckpt 2510: BX: Tj sent an outside analysis of the first scan-study export (v0.58.3, 622
```

(8 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
