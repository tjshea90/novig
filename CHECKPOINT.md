# CHECKPOINT 3410 — read me first, then TASKS.md

**Written:** 2026-10-16T03:16:13Z · **tests:** all 5 fast checks green
**Branch:** `ccr-6cce5eff-xcxliy` · **builds on:** `5a2511066` (this checkpoint is the commit after it)

## Just done
pre-release: v0.114.0: the app as a monitor and control center: Engine and Claude tabs, no share buttons, chat read receipts and attachments, delete messages (versionCode 296, v0.114.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.114.0), then run: bash tools/record-release.sh v0.114.0 296 "v0.114.0: the app as a monitor and control center: Engine and Claude tabs, no share buttons, chat read receipts and attachments, delete messages"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  5a2511066 ckpt 3409: pre-ship: v0.114.0: the app as a monitor and control center: Engine and Cla
  fd62c6d03 ckpt 3408: TCL34 data side: chat ack/files/keep (CloudChat, tell_tj, chat_watch auto-a
  5cca2d4d1 ckpt 3407: TCL34 planned from Tj's in-app chat (3 messages); acknowledged in chat
  8b09cfb5a ckpt 3406: v0.113.2: hours 08:00-01:00, dashboard last-known readings; engine restarte
  702dfcef5 ckpt 3405: pre-release: v0.113.2: the engine runs 8 am to 1 am Eastern; the dashboard 
  8bbda8880 ckpt 3404: pre-ship: v0.113.2: the engine runs 8 am to 1 am Eastern; the dashboard sho
  d6c2a7f09 ckpt 3403: TCL33 shipped v0.113.1: wide scan log, bids journal, flat datasets, bid_sum
  ce192efd9 ckpt 3402: pre-release: v0.113.1: the engine saves every scanned bet (shown or not) an
  9397e56bc ckpt 3401: pre-ship: v0.113.1: the engine saves every scanned bet (shown or not) and e
  6db9a23f1 ckpt 3400: TCL32 shipped v0.113.0: in-app updater
```
