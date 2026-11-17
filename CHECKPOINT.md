# CHECKPOINT 4596 — read me first, then TASKS.md

**Written:** 2026-11-17T16:29:44Z · **tests:** all 10 fast checks green
**Branch:** `claude/optimistic-curie-o1vg2x` · **builds on:** `cb3be976e` (this checkpoint is the commit after it)

## Just done
pre-release: AU1: faster start (no MB parses on main thread), no large-file rewrites, star evidence 11-16 (versionCode 474, v1.0.134)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.134), then run: bash tools/record-release.sh v1.0.134 474 "AU1: faster start (no MB parses on main thread), no large-file rewrites, star evidence 11-16"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  cb3be976e ckpt 4595: pre-ship: AU1: faster start (no MB parses on main thread), no large-file re
  eb8373cd0 ckpt 4594: pre-ship: AU1: faster start (no MB parses on main thread), no large-file re
  3ca038064 ckpt 4593: pre-ship: AU1: faster start (no MB parses on main thread), no large-file re
  ee95eb2c6 ckpt 4592: ANALYST 11-17 slot1: skip rule, watch only, no change
  23fdc16a2 ckpt 4591: AU1: au1-fire.yml durable GitHub timer fires mirage routine 14:10 UTC
  fe798e06a ckpt 4590: AU1 logged + session timer set 14:13 UTC
  ed6e3e667 ckpt 4589: chat run done: PP1 v1.0.133 shipped, LV2 relaxed live rules (paper)
  9db30f0b9 ckpt 4588: PP1 shipped v1.0.133, provenOnly on via cloud-config; engine_set allows pro
  136778400 ckpt 4587: pre-release: PP1: only types proven profitable at Novig (2+ stars) are list
  41d219b29 ckpt 4586: pre-ship: PP1: only types proven profitable at Novig (2+ stars) are listed
```
