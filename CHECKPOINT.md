# CHECKPOINT 3035 — read me first, then TASKS.md

**Written:** 2026-10-12T03:42:18Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `85b470a5a` (this checkpoint is the commit after it)

## Just done
TAN3 rules: bid desk skips bids >1c behind the best bid; bid slider horizon capped 24h (was 48h); tests

## Do this next
TAN3 next: Over-prop extra edge in AutoBet.judge (+bids), memo correction (lead-time CLV bases), then settings audit

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  8e11a6ba3 ckpt 3034: TAN2: bid-desk step in the background cycle time-boxed to the cycle interva
  37d7ae536 ckpt 3033: TAN2: health check ignores removed keys/switched-off APIs; OddsPapi Free fa
  ef2c82a17 ckpt 3032: TAN1 baseline: full floor green (3245 tests, 405 s); TAN3 analysis saved
  853ff8fed ckpt 3031: TAN3: bet/bid analysis scripts + findings memo (lead-time slope strong; Ove
  7caf557ab ckpt 3030: TAN written in TASKS.md (request captured); hooks installed; uploads alread
  38078a2c3 ckpt 3029: TAM1 memo written
  a9caadbf7 ckpt 3028: pre-release: v0.89.34: live bets have no maximum EV (versionCode 219, v0.89
  12fddff34 ckpt 3027: pre-ship: v0.89.34: live bets have no maximum EV
  5144eb8fa ckpt 3026: pre-release: v0.89.33: star filter under the auto-bet slider, slider off wh
  ee3b79ad5 ckpt 3025: pre-ship: v0.89.33: star filter under the auto-bet slider, slider off while
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
