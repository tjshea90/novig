# CHECKPOINT 3418 — read me first, then TASKS.md

**Written:** 2026-10-16T04:24:02Z · **tests:** all 5 fast checks green
**Branch:** `ccr-6cce5eff-xcxliy` · **builds on:** `1a329863a` (this checkpoint is the commit after it)

## Just done
pre-release: v0.114.3: updater retries a refused install with Android's own screen; the app reopens itself after an update (versionCode 299, v0.114.3)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.114.3), then run: bash tools/record-release.sh v0.114.3 299 "v0.114.3: updater retries a refused install with Android's own screen; the app reopens itself after an update"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  1a329863a ckpt 3417: pre-ship: v0.114.3: updater retries a refused install with Android's own sc
  3db6bb8fb ckpt 3416: v0.114.2 shipped: chat new/clear/scroll; batching rule in CLAUDE.md
  724d62f49 ckpt 3415: pre-release: v0.114.2: chat New chat, Clear chat and earlier chats; chat al
  75881bfbf ckpt 3414: pre-ship: v0.114.2: chat New chat, Clear chat and earlier chats; chat alway
  f8abcd09f ckpt 3413: pre-release: v0.114.1: chat keeps its input pinned above the keyboard and t
  b4e59c754 ckpt 3412: pre-ship: v0.114.1: chat keeps its input pinned above the keyboard and the 
  e4bbd85b1 ckpt 3411: TCL34 shipped v0.114.0: monitor layout, chat receipts/attachments/trash, cl
  1d6c30ba9 ckpt 3410: pre-release: v0.114.0: the app as a monitor and control center: Engine and 
  5a2511066 ckpt 3409: pre-ship: v0.114.0: the app as a monitor and control center: Engine and Cla
  fd62c6d03 ckpt 3408: TCL34 data side: chat ack/files/keep (CloudChat, tell_tj, chat_watch auto-a
```
