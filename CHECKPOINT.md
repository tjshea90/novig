# CHECKPOINT 4573 — read me first, then TASKS.md

**Written:** 2026-11-17T03:35:25Z · **tests:** all 10 fast checks green
**Branch:** `claude/optimistic-curie-6rhkmh` · **builds on:** `763cf8da3` (this checkpoint is the commit after it)

## Just done
pre-release: HB1: hand bets blocked and unlisted for types proven to lose (n>=300, t<=-2) (versionCode 470, v1.0.130)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.130), then run: bash tools/record-release.sh v1.0.130 470 "HB1: hand bets blocked and unlisted for types proven to lose (n>=300, t<=-2)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  763cf8da3 ckpt 4572: bump 1.0.130
  49a47c57e ckpt 4571: HB1: blocked types hidden from feed+CNO list too
  e8f2a1fa7 ckpt 4570: HB1 hand hard-block coded + tested (ProfitProfile.handBlock, planner manual
  58f847a81 ckpt 4569: profit-only engine settings pushed; HB1 hand block next
  811e207d7 ckpt 4568: answered Tj: what is proven profitable
  183861a09 ckpt 4567: answered Tj: does anything justify paying
  95519a8a5 ckpt 4566: answered Tj: replacement APIs research
  78b5e7eb8 ckpt 4565: ANALYST 11-17: no change, notes written
  56084c721 ckpt 4564: answered Tj release it: nothing pending; bets store already off-main
  9885b0af3 ckpt 4563: read v1.0.129 diag: coldstart 8.8s, bets.json 8MB
```
