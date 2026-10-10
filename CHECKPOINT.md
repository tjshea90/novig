# CHECKPOINT 2840 — read me first, then TASKS.md

**Written:** 2026-10-10T07:03:36Z · **tests:** all 4 fast checks green
**Branch:** `ccr-bab5067e-obkelv` · **builds on:** `b0584777` (this checkpoint is the commit after it)

## Just done
pre-release: v0.85.4: the live bid desk now actually starts (it never did in v0.85.0-0.85.3) (versionCode 164, v0.85.4)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.85.4), then run: bash tools/record-release.sh v0.85.4 164 "v0.85.4: the live bid desk now actually starts (it never did in v0.85.0-0.85.3)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  b0584777 ckpt 2839: pre-ship: v0.85.4: the live bid desk now actually starts (it never did in v0
  3bc8ffa2 ckpt 2838: v0.85.3 released and recorded
  f132d60f ckpt 2837: pre-release: v0.85.3: live bid desk loop heartbeat and a Details block on th
  6245fed7 ckpt 2836: pre-ship: v0.85.3: live bid desk loop heartbeat and a Details block on the L
  6a173fb1 ckpt 2835: v0.85.2 released and recorded
  144549fd ckpt 2834: pre-release: v0.85.2: live bid engine survives a bad message and says why it
  5fe9b0bd ckpt 2833: pre-ship: v0.85.2: live bid engine survives a bad message and says why it is
  d5a67be7 ckpt 2832: v0.85.1 released and recorded
  6be33f9c ckpt 2831: pre-release: v0.85.1: live bids say why none is up; live orders may pay up t
  d961dee1 ckpt 2830: pre-ship: v0.85.1: live bids say why none is up; live orders may pay up to t
```
