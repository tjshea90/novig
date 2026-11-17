# CHECKPOINT 4577 — read me first, then TASKS.md

**Written:** 2026-11-17T04:48:23Z · **tests:** all 10 fast checks green
**Branch:** `claude/optimistic-curie-cya0uu` · **builds on:** `6ca4611a4` (this checkpoint is the commit after it)

## Just done
pre-release: SV1: bets a sharp book says are not +EV are no longer listed (versionCode 471, v1.0.131)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.131), then run: bash tools/record-release.sh v1.0.131 471 "SV1: bets a sharp book says are not +EV are no longer listed"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6ca4611a4 ckpt 4576: pre-ship: SV1: bets a sharp book says are not +EV are no longer listed
  a6b286e1d ckpt 4575: pre-ship: SV1: bets a sharp book says are not +EV are no longer listed
  68fec10ee ckpt 4574: v1.0.130 released: HB1 hand block
  b7959ebfb ckpt 4573: pre-release: HB1: hand bets blocked and unlisted for types proven to lose (
  763cf8da3 ckpt 4572: bump 1.0.130
  49a47c57e ckpt 4571: HB1: blocked types hidden from feed+CNO list too
  e8f2a1fa7 ckpt 4570: HB1 hand hard-block coded + tested (ProfitProfile.handBlock, planner manual
  58f847a81 ckpt 4569: profit-only engine settings pushed; HB1 hand block next
  811e207d7 ckpt 4568: answered Tj: what is proven profitable
  183861a09 ckpt 4567: answered Tj: does anything justify paying
```
