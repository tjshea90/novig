# CHECKPOINT 3837 — read me first, then TASKS.md

**Written:** 2026-10-20T20:51:06Z · **tests:** all 6 fast checks green
**Branch:** `claude/optimistic-curie-4dhnse` · **builds on:** `5eb5ea12a` (this checkpoint is the commit after it)

## Just done
pre-release: v1.0.20: live bids on obvious +EV totals/spreads (middle tier 65-85% sure, bid 15% under fair) (versionCode 360, v1.0.20)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.20), then run: bash tools/record-release.sh v1.0.20 360 "v1.0.20: live bids on obvious +EV totals/spreads (middle tier 65-85% sure, bid 15% under fair)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  5eb5ea12a ckpt 3836: pre-ship: v1.0.20: live bids on obvious +EV totals/spreads (middle tier 65-
  2c1343606 ckpt 3835: v1.0.19 released (CH40,41)
  747a781ba ckpt 3834: pre-release: v1.0.19: refused-key API light, Kalshi Check key feedback, bid
  21abaa9af ckpt 3833: pre-ship: v1.0.19: refused-key API light, Kalshi Check key feedback, bids m
  8cc77c975 ckpt 3832: pre-ship: CH40-41: refused-key API light, Kalshi Check key feedback, bids m
  68b44bfb9 ckpt 3831: CH40a/b done, tests pass; ship next
  464bc2bb5 ckpt 3830: WATCH 2026-10-20: engine healthy, no action
  a0b40a813 ckpt 3829: CHAT wake: v1.0.16-1.0.18 shipped (CH37-39 Kalshi slip/colours/Engine card/
  b2f6d849b ckpt 3828: pre-release: v1.0.18: Kalshi bids say in words why they are not posting (no
  861b1a5bb ckpt 3827: pre-ship: v1.0.18: Kalshi bids say in words why they are not posting (no ke
```
