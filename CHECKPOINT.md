# CHECKPOINT 4600 — read me first, then TASKS.md

**Written:** 2026-11-17T17:15:59Z · **tests:** all 10 fast checks green
**Branch:** `claude/optimistic-curie-ydwd3a` · **builds on:** `e9cabde5b` (this checkpoint is the commit after it)

## Just done
pre-release: TR2 stable all-time profit reading, PF11 +EV tab no double rebuild (versionCode 475, v1.0.135)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.135), then run: bash tools/record-release.sh v1.0.135 475 "TR2 stable all-time profit reading, PF11 +EV tab no double rebuild"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  e9cabde5b ckpt 4599: pre-ship: TR2 stable all-time profit reading, PF11 +EV tab no double rebuil
  05bb211bb ckpt 4598: TR2 wallet stable read, PF11 feed memo; running ship
  36e17a379 ckpt 4597: AU1 v1.0.134 shipped
  bc13c1406 ckpt 4596: pre-release: AU1: faster start (no MB parses on main thread), no large-file
  cb3be976e ckpt 4595: pre-ship: AU1: faster start (no MB parses on main thread), no large-file re
  eb8373cd0 ckpt 4594: pre-ship: AU1: faster start (no MB parses on main thread), no large-file re
  3ca038064 ckpt 4593: pre-ship: AU1: faster start (no MB parses on main thread), no large-file re
  ee95eb2c6 ckpt 4592: ANALYST 11-17 slot1: skip rule, watch only, no change
  23fdc16a2 ckpt 4591: AU1: au1-fire.yml durable GitHub timer fires mirage routine 14:10 UTC
  fe798e06a ckpt 4590: AU1 logged + session timer set 14:13 UTC
```
