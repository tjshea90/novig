# CHECKPOINT 3736 — read me first, then TASKS.md

**Written:** 2026-10-19T06:28:10Z · **tests:** all 5 fast checks green
**Branch:** `claude/optimistic-curie-wb4vfj` · **builds on:** `65a71c1ef` (this checkpoint is the commit after it)

## Just done
pre-release: v1.0.5: tennis on in live bidding; P/L = won minus staked, % over staked; chat one-reply guard (versionCode 345, v1.0.5)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.5), then run: bash tools/record-release.sh v1.0.5 345 "v1.0.5: tennis on in live bidding; P/L = won minus staked, % over staked; chat one-reply guard"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  cdc45535b ckpt 3735: pre-ship: v1.0.5: tennis on in live bidding; P/L = won minus staked, % over
  6679d493a ckpt 3734: merged with session B; tennis+P/L on staked ready
  1ee438c0a ckpt 3730: tennis live ON, P/L on staked, chat one-reply guard
  1f9d326b9 ckpt 3733: fix merged conflict in tell_tj
  675a814e6 ckpt 3732: CH20 tennis live default on, CH18c one-answer guard
  eb56179ba ckpt 3731: tell_tj selftest fixed for stale guard
  5345f85a7 ckpt 3729: chat: 15 min window, old-reply guard
  c010057a5 ckpt 3730: CH18b stale-reply guard
  4bc23a1ca ckpt 3729: CH19 answered
  55c39c7e9 ckpt 3728: CH18 chat presence heartbeat + wake fix
```
