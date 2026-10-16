# CHECKPOINT 3422 — read me first, then TASKS.md

**Written:** 2026-10-16T04:53:19Z · **tests:** all 5 fast checks green
**Branch:** `ccr-6cce5eff-xcxliy` · **builds on:** `0aa5040d4` (this checkpoint is the commit after it)

## Just done
pre-release: v0.114.4: CNO tab back in the bar; the phone's auto-bet keeps using CNO beside the engine (versionCode 300, v0.114.4)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.114.4), then run: bash tools/record-release.sh v0.114.4 300 "v0.114.4: CNO tab back in the bar; the phone's auto-bet keeps using CNO beside the engine"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  0aa5040d4 ckpt 3421: pre-ship: v0.114.4: CNO tab back in the bar; the phone's auto-bet keeps usi
  5ab4884f0 ckpt 3420: fix engine-grade.yml YAML (a colon in a step name broke it since 01:46Z: no
  798a7c5d3 ckpt 3419: v0.114.3 shipped: updater retry + reopen
  92ad74132 ckpt 3418: pre-release: v0.114.3: updater retries a refused install with Android's own
  1a329863a ckpt 3417: pre-ship: v0.114.3: updater retries a refused install with Android's own sc
  3db6bb8fb ckpt 3416: v0.114.2 shipped: chat new/clear/scroll; batching rule in CLAUDE.md
  724d62f49 ckpt 3415: pre-release: v0.114.2: chat New chat, Clear chat and earlier chats; chat al
  75881bfbf ckpt 3414: pre-ship: v0.114.2: chat New chat, Clear chat and earlier chats; chat alway
  f8abcd09f ckpt 3413: pre-release: v0.114.1: chat keeps its input pinned above the keyboard and t
  b4e59c754 ckpt 3412: pre-ship: v0.114.1: chat keeps its input pinned above the keyboard and the 
```
