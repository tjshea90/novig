# CHECKPOINT 3883 — read me first, then TASKS.md

**Written:** 2026-10-21T04:51:19Z · **tests:** all 6 fast checks green
**Branch:** `claude/optimistic-curie-7dry2r` · **builds on:** `b610dc97e` (this checkpoint is the commit after it)

## Just done
pre-release: v1.0.30: refresh arrow flashes, status bar names what is loading (versionCode 370, v1.0.30)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.30), then run: bash tools/record-release.sh v1.0.30 370 "v1.0.30: refresh arrow flashes, status bar names what is loading"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  b610dc97e ckpt 3882: CH52: pull arrow flashes 1s, status bar shows read in flight
  db4acd910 ckpt 3881: pre-release: v1.0.29: pull-to-refresh deadlines, Stopping headline when swi
  f076e3bff ckpt 3880: CH51: dashboard shows Stopping when switch off; refreshNow reads have 20s d
  ae9575801 ckpt 3875: v1.0.27 released; chat quiet 30 min
  6c12a33be ckpt 3879: v1.0.28 released and recorded; told Tj
  70c7ac506 ckpt 3878: v1.0.28 laned scan shipped, release.yml triggered
  47a50bfcf ckpt 3877: pre-release: v1.0.28: laned scan on the engine: quick 48 h scans between fu
  6e76f9766 ckpt 3876: pre-ship: v1.0.28: laned scan on the engine: quick 48 h scans between full 
  7a72f94db ckpt 3875: pre-ship: v1.0.28: laned scan on the engine: quick 48 h scans between full 
  527e1969a ckpt 3874: pre-release: v1.0.27: ParlayAPI Pro joins the hot refresh for games about t
```
