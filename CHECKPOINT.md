# CHECKPOINT 3157 — read me first, then TASKS.md

**Written:** 2026-10-13T18:38:29Z · **tests:** all 4 fast checks green
**Branch:** `ccr-18ecef04-mledpg` · **builds on:** `87b583276` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.96.1: the card shows the stars the auto-bet holds against your minimum (Profit-proven only lowers them), and the auto-bet's message names both numbers

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
    M  TASKS.md
    M  app/build.gradle.kts
    M  data/src/test/kotlin/com/tjshea/vigilant/data/novig/trading/AutoBetStarsTest.kt

## Last ten checkpoints
```
  e7be19b36 ckpt 3156: TST tasks written
  77953031a ckpt 3155: CI green on main; release.yml triggered for v0.96.0
  edb028a7a ckpt 3154: v0.96.0 shipped to main (local floor green 3461); CI run 38445490938 pendin
  3d7432fb9 ckpt 3153: pre-release: v0.96.0: More, smaller bids (Bids slider switch), bets leave t
  6b423bd61 ckpt 3152: pre-ship: v0.96.0: More, smaller bids (Bids slider switch), bets leave the 
  c5b286af0 ckpt 3151: pre-ship: v0.96.0: More, smaller bids (Bids slider switch), bets leave the 
  8bf749d75 ckpt 3150: pre-ship: v0.96.0: More, smaller bids (Bids slider switch), bets leave the 
  348b730f0 ckpt 3149: TMB3: provisional hide + restore after miss, tested; TMB4 code (cnoShown -E
  85f752848 ckpt 3148: TMB1-2: BidTier + many-bids mode (data layer, UI switch) tested
  a076e6875 ckpt 3147: TMB tasks written
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
