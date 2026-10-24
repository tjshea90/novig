# CHECKPOINT 4108 — read me first, then TASKS.md

**Written:** 2026-10-24T06:27:44Z · **tests:** all 9 fast checks green
**Branch:** `ccr-305d3f7a-i2z8ja` · **builds on:** `29de598fa` (this checkpoint is the commit after it)

## Just done
SX findings 4-5: Novig's own close is the yardstick (ROI = 0.82 x CLV_novig); outside-books edges show negative CLV vs Novig close in the backtest; but the backtest's type table fails validation on real Vigilant/CNO lines, so not shipped. Streaming Novig public trades 10-12..10-22 for the real lines' markets

## Do this next
join trades -> Novig close (last pregame trades) per real line, grade real lines on CLV_novig by every input, design stars from what orders them, validate halves

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M research/star_rating_sx_2026-10-24.md

## Last ten checkpoints
```
  398215421 ckpt 4107: propline accuracy task: try each comma-separated key
  43909e77f ckpt 4106: propline.yml task accuracy (PL-OA)
  cfe7f1b9d ckpt 4105: PropLine openapi saved, task PL-OA written
  b7e8e5b26 ckpt 4106: SX findings 2-3 written (point-in-time multiplicative edge lost; favourite-
  7617623f2 ckpt 4105: SX finding 2 (multiplicative devig): +EV Novig lines lost ROI -5..-6% at ev
  49c19f9e1 ckpt 4104: pre-release: 1xBet under any feed spelling (1xbet/onexbet) is one company, 
  edea0e6c8 ckpt 4103: pre-ship: 1xBet under any feed spelling (1xbet/onexbet) is one company, not
  552f56908 ckpt 4102: pre-release: CH75: a bet nobody is selling stays off the list until there i
  bfdcd336d ckpt 4104: SX: star-study split into 4 parallel shards (one 4000-game job would near t
  3aa9a80ef ckpt 4103: SX code WIP: BetRating Scale.SX (proofStars from StarEvidence z, losing typ
```

(11 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
