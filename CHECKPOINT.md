# CHECKPOINT 2932 — read me first, then TASKS.md

**Written:** 2026-10-11T04:37:18Z · **tests:** all 4 fast checks green
**Branch:** `claude/unabated-oddsapi-integration-1u6kdc` · **builds on:** `f174970c` (this checkpoint is the commit after it)

## Just done
TD: Safety/Volume slider (LiveBidSlider + LiveBidSliderTest, desk evAtFill + kept-EV halt), Live bids page redesigned (slider, what to bid on, money; everything else under Advanced), UI test; v0.89.6 bump

## Do this next
ship v0.89.6 (bash ship.sh), trigger release.yml, confirm, record-release; tell Tj: no bids = live-bid Tennis switch off, it's now under 'What to bid on'

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  cbd0b033 ckpt 2931: pre-release: v0.89.5: bid guard (cancels resting orders with no expiry after
  8796d96e ckpt 2930: pre-ship: v0.89.5: bid guard (cancels resting orders with no expiry after 30
  7a0104c1 ckpt 2929: pre-ship: v0.89.5: bid guard (cancels resting orders with no expiry after 30
  fb078545 ckpt 2928: pre-release: v0.89.4: tennis bids (plain rules for ATP/WTA, lean scan keeps 
  edb95cd5 ckpt 2927: pre-ship: v0.89.4: tennis bids (plain rules for ATP/WTA, lean scan keeps gam
  40eda833 ckpt 2926: pre-ship: v0.89.4: tennis bids (plain rules for ATP/WTA, lean scan keeps gam
  3f9274b1 ckpt 2925: pre-release: v0.89.3: OddsPapi (v5) switch to price the fair average with ev
  f118e846 ckpt 2924: pre-ship: v0.89.3: OddsPapi (v5) switch to price the fair average with every
  ba65a365 ckpt 2923: pre-release: v0.89.2: Unabated fixed from the real sample (bet-type filtered
  69bf0afd ckpt 2922: pre-ship: v0.89.2: Unabated fixed from the real sample (bet-type filtered bo
```

(10 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
