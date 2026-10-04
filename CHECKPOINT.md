# CHECKPOINT 2526 — read me first, then TASKS.md

**Written:** 2026-10-04T21:29:53Z · **tests:** all 3 fast checks green
**Branch:** `claude/novig-scanning-bet-exposure-k97qvu` · **builds on:** `b95993fb` (this checkpoint is the commit after it)

## Just done
pre-release: v0.60.0: a closed market no longer sends the Novig scan to the slow public route for ten minutes (one 404 did); a small $ in game button next to every bet shows what you already have on that game; bids can rank popular markets first (Novig's own volume) and require a sharp book (off); study and diagnostics fixes (versionCode 107, v0.60.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.60.0), then run: bash tools/record-release.sh v0.60.0 107 "v0.60.0: a closed market no longer sends the Novig scan to the slow public route for ten minutes (one 404 did); a small $ in game button next to every bet shows what you already have on that game; bids can rank popular markets first (Novig's own volume) and require a sharp book (off); study and diagnostics fixes"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6beff33a ckpt 2525: BZ5 built: GameBets (pure index of open bets + resting bids by game), GameBe
  2f8e43b9 ckpt 2524: BZ: wrote Tj's request (slow Novig scan, study sanity, bids 3.5/3.25%, per-g
  9d745d97 ckpt 2523: v0.59.1 released + recorded (release.yml run 37186059558 green, CI green on 
  b61910e6 ckpt 2522: pre-release: v0.59.1: Most at risk on one game now has a $5 chip and a box t
  79c8bccc ckpt 2521: v0.59.0 released + recorded (release.yml run 37178804663 green, tag v0.59.0,
  0ac10a65 ckpt 2520: pre-release: v0.59.0: most at risk on one game (Settings › Betting & Novig
  519ec408 ckpt 2519: BW5 prep: desk-level maker tests (held bets + bids kept across passes), Make
  6fea08cf ckpt 2518: BW4 done in code: maker (MakerRules.maxPerGame, RestingBid.game, MakerPlan h
  229fab48 ckpt 2517: BW4 step 2+3: ScanSettings.apiMaxPerGame (default 25, 0 = none) -> BetLimits
  a99026eb ckpt 2516: BW4 step 1: GameExposure (pure) + GameRef + TrackedBet.eventId (set by logAp
```

(45 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
