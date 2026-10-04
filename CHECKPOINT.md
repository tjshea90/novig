# CHECKPOINT 2525 — read me first, then TASKS.md

**Written:** 2026-10-04T20:42:21Z · **tests:** all 3 fast checks green
**Branch:** `claude/novig-scanning-bet-exposure-k97qvu` · **builds on:** `2fb59366` (this checkpoint is the commit after it)

## Just done
BZ5 built: GameBets (pure index of open bets + resting bids by game), GameBetsUi (small '$21.30 in game ▾' button + sheet), on +EV/CNO/ParlayAPI/Games/Tracker cards; data 11 + UI 10 tests; 17 mutants all killed. BZ1 root cause found: ONE 404 on the keyed book route takes the whole key route down for 10 min (NovigPublicClient.books catch NovigApiException -> keyedDownUntil) so the scan falls to the public route at 2-4/s; log timestamps 13:12:11 and 16:01:07 match

## Do this next
BZ1: fix books() so a per-market 404 / one 5xx is that book's failure not a verdict on the key (test with mock server), record key standdowns in Diagnostics; BZ2/BZ3/BZ4 analysis; then full floor, ship, release

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
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

(21 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
