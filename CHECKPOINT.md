# CHECKPOINT 2935 — read me first, then TASKS.md

**Written:** 2026-10-11T05:04:43Z · **tests:** all 4 fast checks green
**Branch:** `claude/unabated-oddsapi-integration-1u6kdc` · **builds on:** `7843a521` (this checkpoint is the commit after it)

## Just done
TE/TD: OddsPapi Free 404 FIXTURE_NOT_FOUND = empty window/no odds (not a failed scan); Unabated sources fall back when one league's /sources fails; whyFew points to Tennis switch; schema tests -> 16; v0.89.8

## Do this next
ship v0.89.8, release, record; tell Tj: max-volume no bids = every live game tennis + Tennis switch off; Unabated NFL/NBA/CFB never read because /sources failed (fixed)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  3a19cb83 ckpt 2934: TE: OddsPapi Free fair-odds feed now on by default (10/day, schema 16 migrat
  7244f0ee ckpt 2933: pre-release: v0.89.6: live bids Safety/Volume slider (one control sets margi
  3c07f0a9 ckpt 2932: TD: Safety/Volume slider (LiveBidSlider + LiveBidSliderTest, desk evAtFill +
  cbd0b033 ckpt 2931: pre-release: v0.89.5: bid guard (cancels resting orders with no expiry after
  8796d96e ckpt 2930: pre-ship: v0.89.5: bid guard (cancels resting orders with no expiry after 30
  7a0104c1 ckpt 2929: pre-ship: v0.89.5: bid guard (cancels resting orders with no expiry after 30
  fb078545 ckpt 2928: pre-release: v0.89.4: tennis bids (plain rules for ATP/WTA, lean scan keeps 
  edb95cd5 ckpt 2927: pre-ship: v0.89.4: tennis bids (plain rules for ATP/WTA, lean scan keeps gam
  40eda833 ckpt 2926: pre-ship: v0.89.4: tennis bids (plain rules for ATP/WTA, lean scan keeps gam
  3f9274b1 ckpt 2925: pre-release: v0.89.3: OddsPapi (v5) switch to price the fair average with ev
```

(11 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
