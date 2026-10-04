# CHECKPOINT 2522 — read me first, then TASKS.md

**Written:** 2026-10-04T07:24:29Z · **tests:** all 3 fast checks green
**Branch:** `ccr-55a7238c-jfgers` · **builds on:** `75bdcd3d` (this checkpoint is the commit after it)

## Just done
pre-release: v0.59.1: Most at risk on one game now has a $5 chip and a box to type any amount (dollars and cents, up to $10,000; saved as you type; No limit stays its own chip) (versionCode 106, v0.59.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.59.1), then run: bash tools/record-release.sh v0.59.1 106 "v0.59.1: Most at risk on one game now has a $5 chip and a box to type any amount (dollars and cents, up to $10,000; saved as you type; No limit stays its own chip)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  79c8bccc ckpt 2521: v0.59.0 released + recorded (release.yml run 37178804663 green, tag v0.59.0,
  0ac10a65 ckpt 2520: pre-release: v0.59.0: most at risk on one game (Settings › Betting & Novig
  519ec408 ckpt 2519: BW5 prep: desk-level maker tests (held bets + bids kept across passes), Make
  6fea08cf ckpt 2518: BW4 done in code: maker (MakerRules.maxPerGame, RestingBid.game, MakerPlan h
  229fab48 ckpt 2517: BW4 step 2+3: ScanSettings.apiMaxPerGame (default 25, 0 = none) -> BetLimits
  a99026eb ckpt 2516: BW4 step 1: GameExposure (pure) + GameRef + TrackedBet.eventId (set by logAp
  38b2cca8 ckpt 2515: BW1 done (read-only): no per-game limit exists; game id missing on TrackedBe
  24a5e562 ckpt 2514: v0.58.4 released + recorded (release.yml run 37174473407 green, tag v0.58.4,
  9b32300f ckpt 2513: pre-release: v0.58.4: a close from another game can no longer count (a Washi
  2273ba90 ckpt 2512: BX3 log-only fixes done in data: rules line now says CNO's edge/odds/books, 
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
