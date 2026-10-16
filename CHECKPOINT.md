# CHECKPOINT 3405 — read me first, then TASKS.md

**Written:** 2026-10-16T02:43:12Z · **tests:** all 5 fast checks green
**Branch:** `ccr-6cce5eff-xcxliy` · **builds on:** `8bbda8880` (this checkpoint is the commit after it)

## Just done
pre-release: v0.113.2: the engine runs 8 am to 1 am Eastern; the dashboard shows last-known readings as such when the engine is not running (versionCode 295, v0.113.2)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.113.2), then run: bash tools/record-release.sh v0.113.2 295 "v0.113.2: the engine runs 8 am to 1 am Eastern; the dashboard shows last-known readings as such when the engine is not running"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  8bbda8880 ckpt 3404: pre-ship: v0.113.2: the engine runs 8 am to 1 am Eastern; the dashboard sho
  d6c2a7f09 ckpt 3403: TCL33 shipped v0.113.1: wide scan log, bids journal, flat datasets, bid_sum
  ce192efd9 ckpt 3402: pre-release: v0.113.1: the engine saves every scanned bet (shown or not) an
  9397e56bc ckpt 3401: pre-ship: v0.113.1: the engine saves every scanned bet (shown or not) and e
  6db9a23f1 ckpt 3400: TCL32 shipped v0.113.0: in-app updater
  2971bfaeb ckpt 3399: pre-release: v0.113.0: app updates from GitHub: notification, Install updat
  6f2fdfddd ckpt 3398: pre-ship: v0.113.0: app updates from GitHub: notification, Install update b
  4e59400bd ckpt 3397: pre-ship: v0.113.0: app updates from GitHub: notification, Install update b
  8c71b6585 ckpt 3396: TCL31 shipped v0.112.0: engine posted 194 live bids on first pass; released
  8899d4553 ckpt 3395: pre-release: v0.112.0: the GitHub engine posts bids too; the phone syncs ev
```
