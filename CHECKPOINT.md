# CHECKPOINT 2931 — read me first, then TASKS.md

**Written:** 2026-10-11T04:29:00Z · **tests:** all 4 fast checks green
**Branch:** `claude/unabated-oddsapi-integration-1u6kdc` · **builds on:** `8796d96e` (this checkpoint is the commit after it)

## Just done
pre-release: v0.89.5: bid guard (cancels resting orders with no expiry after 30 min, whoever posted them) (versionCode 190, v0.89.5)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.89.5), then run: bash tools/record-release.sh v0.89.5 190 "v0.89.5: bid guard (cancels resting orders with no expiry after 30 min, whoever posted them)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  8796d96e ckpt 2930: pre-ship: v0.89.5: bid guard (cancels resting orders with no expiry after 30
  7a0104c1 ckpt 2929: pre-ship: v0.89.5: bid guard (cancels resting orders with no expiry after 30
  fb078545 ckpt 2928: pre-release: v0.89.4: tennis bids (plain rules for ATP/WTA, lean scan keeps 
  edb95cd5 ckpt 2927: pre-ship: v0.89.4: tennis bids (plain rules for ATP/WTA, lean scan keeps gam
  40eda833 ckpt 2926: pre-ship: v0.89.4: tennis bids (plain rules for ATP/WTA, lean scan keeps gam
  3f9274b1 ckpt 2925: pre-release: v0.89.3: OddsPapi (v5) switch to price the fair average with ev
  f118e846 ckpt 2924: pre-ship: v0.89.3: OddsPapi (v5) switch to price the fair average with every
  ba65a365 ckpt 2923: pre-release: v0.89.2: Unabated fixed from the real sample (bet-type filtered
  69bf0afd ckpt 2922: pre-ship: v0.89.2: Unabated fixed from the real sample (bet-type filtered bo
  8edfb143 ckpt 2921: pre-release: v0.89.1: usage bars back on Settings > APIs (a green-amber-red 
```
