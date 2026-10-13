# CHECKPOINT 3163 — read me first, then TASKS.md

**Written:** 2026-10-13T18:56:04Z · **tests:** all 4 fast checks green
**Branch:** `ccr-18ecef04-mledpg` · **builds on:** `a910bb683` (this checkpoint is the commit after it)

## Just done
pre-release: v0.96.2: NHL shots-on-goal fair prices built from fewer than 5 books on a game over 12 h away are cut 2.5 points (switch in Settings) (versionCode 249, v0.96.2)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.96.2), then run: bash tools/record-release.sh v0.96.2 249 "v0.96.2: NHL shots-on-goal fair prices built from fewer than 5 books on a game over 12 h away are cut 2.5 points (switch in Settings)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  a910bb683 ckpt 3162: pre-ship: v0.96.2: NHL shots-on-goal fair prices built from fewer than 5 bo
  d2ed1b8a4 ckpt 3161: TSOH tasks written
  09a2fa56a ckpt 3160: TSOG: matching audited + ShotsOnGoalMatchTest; research/nhl_sog_matching_20
  91a7e2495 ckpt 3159: TSOG tasks written
  c7a47a248 ckpt 3158: pre-release: v0.96.1: the card shows the stars the auto-bet holds against y
  c68b694d2 ckpt 3157: pre-ship: v0.96.1: the card shows the stars the auto-bet holds against your
  e7be19b36 ckpt 3156: TST tasks written
  77953031a ckpt 3155: CI green on main; release.yml triggered for v0.96.0
  edb028a7a ckpt 3154: v0.96.0 shipped to main (local floor green 3461); CI run 38445490938 pendin
  3d7432fb9 ckpt 3153: pre-release: v0.96.0: More, smaller bids (Bids slider switch), bets leave t
```
