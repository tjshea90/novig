# CHECKPOINT 2934 — read me first, then TASKS.md

**Written:** 2026-10-11T04:55:01Z · **tests:** all 4 fast checks green
**Branch:** `claude/unabated-oddsapi-integration-1u6kdc` · **builds on:** `bbec70b2` (this checkpoint is the commit after it)

## Just done
TE: OddsPapi Free fair-odds feed now on by default (10/day, schema 16 migrates 0->10) so the switch scans; Unabated/Polymarket-only cause still unproven (no fresh Diagnostics)

## Do this next
ship v0.89.7; ask Tj for a Diagnostics right after a scan with only Unabated+OddsPapi Free on to read the per-source report; then TE1/TE2

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  7244f0ee ckpt 2933: pre-release: v0.89.6: live bids Safety/Volume slider (one control sets margi
  3c07f0a9 ckpt 2932: TD: Safety/Volume slider (LiveBidSlider + LiveBidSliderTest, desk evAtFill +
  cbd0b033 ckpt 2931: pre-release: v0.89.5: bid guard (cancels resting orders with no expiry after
  8796d96e ckpt 2930: pre-ship: v0.89.5: bid guard (cancels resting orders with no expiry after 30
  7a0104c1 ckpt 2929: pre-ship: v0.89.5: bid guard (cancels resting orders with no expiry after 30
  fb078545 ckpt 2928: pre-release: v0.89.4: tennis bids (plain rules for ATP/WTA, lean scan keeps 
  edb95cd5 ckpt 2927: pre-ship: v0.89.4: tennis bids (plain rules for ATP/WTA, lean scan keeps gam
  40eda833 ckpt 2926: pre-ship: v0.89.4: tennis bids (plain rules for ATP/WTA, lean scan keeps gam
  3f9274b1 ckpt 2925: pre-release: v0.89.3: OddsPapi (v5) switch to price the fair average with ev
  f118e846 ckpt 2924: pre-ship: v0.89.3: OddsPapi (v5) switch to price the fair average with every
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
