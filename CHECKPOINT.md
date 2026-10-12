# CHECKPOINT 3034 — read me first, then TASKS.md

**Written:** 2026-10-12T03:38:53Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `4edd56131` (this checkpoint is the commit after it)

## Just done
TAN2: bid-desk step in the background cycle time-boxed to the cycle interval (20-90 s) so the CNO read/auto-bet loop is not held 60-120 s; duplicate-bet check no longer flags two separately filled orders

## Do this next
TAN3 rules next: read AutoBet.judge / MakerRules / LevelSliders / Presets for where to add Over-prop margin + bid desk behind-the-book/horizon rules

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  37d7ae536 ckpt 3033: TAN2: health check ignores removed keys/switched-off APIs; OddsPapi Free fa
  ef2c82a17 ckpt 3032: TAN1 baseline: full floor green (3245 tests, 405 s); TAN3 analysis saved
  853ff8fed ckpt 3031: TAN3: bet/bid analysis scripts + findings memo (lead-time slope strong; Ove
  7caf557ab ckpt 3030: TAN written in TASKS.md (request captured); hooks installed; uploads alread
  38078a2c3 ckpt 3029: TAM1 memo written
  a9caadbf7 ckpt 3028: pre-release: v0.89.34: live bets have no maximum EV (versionCode 219, v0.89
  12fddff34 ckpt 3027: pre-ship: v0.89.34: live bets have no maximum EV
  5144eb8fa ckpt 3026: pre-release: v0.89.33: star filter under the auto-bet slider, slider off wh
  ee3b79ad5 ckpt 3025: pre-ship: v0.89.33: star filter under the auto-bet slider, slider off while
  3f39cd806 ckpt 3024: pre-release: v0.89.32: no longshot levels on the sliders, scans give way to
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
