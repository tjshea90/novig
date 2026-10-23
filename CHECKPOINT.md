# CHECKPOINT 4035 — read me first, then TASKS.md

**Written:** 2026-10-23T01:42:03Z · **tests:** all 9 fast checks green
**Branch:** `claude/optimistic-curie-lpr7ge` · **builds on:** `09b8d77e1` (this checkpoint is the commit after it)

## Just done
pre-release: CH68c Kalshi refusal shows the cash on each exchange shard; the retired old order route is no longer tried first (versionCode 397, v1.0.57)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.57), then run: bash tools/record-release.sh v1.0.57 397 "CH68c Kalshi refusal shows the cash on each exchange shard; the retired old order route is no longer tried first"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  09b8d77e1 ckpt 4034: CH68c: shard breakdown in refusal; NO-first removed; bump 1.0.57
  3bc4a2ed5 ckpt 4033: pre-release: CH68b Kalshi NO buys use the order form that names the NO side
  165053371 ckpt 4032: CH68b: NO buys try the legacy route first; bump 1.0.56
  f91f27fe9 ckpt 4031: CH69 noted: ParlayAPI meter mismatch; v1.0.55 out
  26fb8fb98 ckpt 4030: pre-release: CH68a Kalshi refusal shows the exact order sent and Kalshi's c
  a5e9986dc ckpt 4029: CH68a: refusal shows the order sent and Kalshi cash now; bump 1.0.55
  f5a94cb7b ckpt 4028: pre-release: CH68a Kalshi bet card shows Kalshi's exact refusal words (to f
  2c199b37a ckpt 4027: CH68a card shows Kalshi's exact refusal; bump 1.0.54
  2c3c40a9e ckpt 4026: pre-release: CH66a with GitHub driving, the phone takes no Novig/order sock
  be74f1ef9 ckpt 4025: pre-ship: CH66a with GitHub driving, the phone takes no Novig/order sockets
```
