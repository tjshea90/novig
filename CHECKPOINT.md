# CHECKPOINT 3158 — read me first, then TASKS.md

**Written:** 2026-10-13T18:41:15Z · **tests:** all 4 fast checks green
**Branch:** `ccr-18ecef04-mledpg` · **builds on:** `c68b694d2` (this checkpoint is the commit after it)

## Just done
pre-release: v0.96.1: the card shows the stars the auto-bet holds against your minimum (Profit-proven only lowers them), and the auto-bet's message names both numbers (versionCode 248, v0.96.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.96.1), then run: bash tools/record-release.sh v0.96.1 248 "v0.96.1: the card shows the stars the auto-bet holds against your minimum (Profit-proven only lowers them), and the auto-bet's message names both numbers"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  c68b694d2 ckpt 3157: pre-ship: v0.96.1: the card shows the stars the auto-bet holds against your
  e7be19b36 ckpt 3156: TST tasks written
  77953031a ckpt 3155: CI green on main; release.yml triggered for v0.96.0
  edb028a7a ckpt 3154: v0.96.0 shipped to main (local floor green 3461); CI run 38445490938 pendin
  3d7432fb9 ckpt 3153: pre-release: v0.96.0: More, smaller bids (Bids slider switch), bets leave t
  6b423bd61 ckpt 3152: pre-ship: v0.96.0: More, smaller bids (Bids slider switch), bets leave the 
  c5b286af0 ckpt 3151: pre-ship: v0.96.0: More, smaller bids (Bids slider switch), bets leave the 
  8bf749d75 ckpt 3150: pre-ship: v0.96.0: More, smaller bids (Bids slider switch), bets leave the 
  348b730f0 ckpt 3149: TMB3: provisional hide + restore after miss, tested; TMB4 code (cnoShown -E
  85f752848 ckpt 3148: TMB1-2: BidTier + many-bids mode (data layer, UI switch) tested
```
