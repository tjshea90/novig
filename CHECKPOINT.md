# CHECKPOINT 3434 — read me first, then TASKS.md

**Written:** 2026-10-16T16:27:34Z · **tests:** all 5 fast checks green
**Branch:** `ccr-6cce5eff-xcxliy` · **builds on:** `e778af458` (this checkpoint is the commit after it)

## Just done
pre-release: v0.114.5: the chat refreshes the moment the app comes back after the phone was off (versionCode 301, v0.114.5)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.114.5), then run: bash tools/record-release.sh v0.114.5 301 "v0.114.5: the chat refreshes the moment the app comes back after the phone was off"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  e778af458 ckpt 3433: pre-ship: v0.114.5: the chat refreshes the moment the app comes back after 
  87e39ba2e ckpt 3432: TCL43: bolt multi-key already supported; answered
  3e5964d35 ckpt 3431: TCL42a: chat loop wakes on app resume
  16cbd85f8 ckpt 3430: engine run 55 live: bids resume (494 resting); Bolt still 429
  304c35bf3 ckpt 3429: TCL40\/41 answered; engine restarted on new code (Bolt retry, bid guard 1h\
  b5be1a9d0 ckpt 3428: TCL40: Bolt snapshot retries in 60s + 429 detail; engine bid guard pauses 1
  7db014870 ckpt 3427: TCL39a: SGO 403 'Inactive API key' now refuses the key (rotation + advisor 
  60f49130c ckpt 3426: TCL38: answered why no bids (picked-off pause, SGO key inactive)
  4a850946c ckpt 3425: bid fill-rate by lead time recorded
  914580f6b ckpt 3424: first bid analysis written to CHANGES.md (17/6829 filled, no rule change)
```
