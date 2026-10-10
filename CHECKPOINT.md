# CHECKPOINT 2842 — read me first, then TASKS.md

**Written:** 2026-10-10T07:14:39Z · **tests:** all 4 fast checks green
**Branch:** `ccr-bab5067e-obkelv` · **builds on:** `51ceaeea` (this checkpoint is the commit after it)

## Just done
pre-release: v0.85.5: diagnostics file reads the last 3 days of each recorder, says where it is stuck, and old recorder data can be cleared (versionCode 165, v0.85.5)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.85.5), then run: bash tools/record-release.sh v0.85.5 165 "v0.85.5: diagnostics file reads the last 3 days of each recorder, says where it is stuck, and old recorder data can be cleared"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  51ceaeea ckpt 2841: pre-ship: v0.85.5: diagnostics file reads the last 3 days of each recorder, 
  fe1140c8 ckpt 2840: pre-release: v0.85.4: the live bid desk now actually starts (it never did in
  b0584777 ckpt 2839: pre-ship: v0.85.4: the live bid desk now actually starts (it never did in v0
  3bc8ffa2 ckpt 2838: v0.85.3 released and recorded
  f132d60f ckpt 2837: pre-release: v0.85.3: live bid desk loop heartbeat and a Details block on th
  6245fed7 ckpt 2836: pre-ship: v0.85.3: live bid desk loop heartbeat and a Details block on the L
  6a173fb1 ckpt 2835: v0.85.2 released and recorded
  144549fd ckpt 2834: pre-release: v0.85.2: live bid engine survives a bad message and says why it
  5fe9b0bd ckpt 2833: pre-ship: v0.85.2: live bid engine survives a bad message and says why it is
  d5a67be7 ckpt 2832: v0.85.1 released and recorded
```
