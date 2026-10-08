# CHECKPOINT 2732 — read me first, then TASKS.md

**Written:** 2026-10-08T02:34:19Z · **tests:** all 4 fast checks green
**Branch:** `ccr-f7044881-1c7epp` · **builds on:** `2963905b` (this checkpoint is the commit after it)

## Just done
pre-release: v0.76.1: Pinnodds live runs in the background with the screen off (foreground service with a wake lock, Stop and STOP ALL on its notification); doubleheader-safe game matching; alternate-line moves no longer wake the engine (versionCode 135, v0.76.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.76.1), then run: bash tools/record-release.sh v0.76.1 135 "v0.76.1: Pinnodds live runs in the background with the screen off (foreground service with a wake lock, Stop and STOP ALL on its notification); doubleheader-safe game matching; alternate-line moves no longer wake the engine"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  2963905b ckpt 2731: v0.76.1 ready: LiveFeedService (foreground service holding the CPU while the
  b6b1c150 ckpt 2730: pre-release: v0.76.0: Pinnodds live (Pinnacle vs Novig): Pinnodds WebSocket 
  3e95a5d8 ckpt 2729: Pinnodds live ready to ship: all-sports league list, final pooled study (52 
  61095455 ckpt 2728: Pinnodds live: UI test + settings search entries, MockWebServer tests for th
  9a4d414e ckpt 2727: Pinnodds live: app wiring done (ApiProvider.PINNODDS, ScanSettings pinnLive*
  6136ef32 ckpt 2726: PINNODDS LIVE (TASKS.md PW*): read pinnodds docs + tested the key (trial_dem
  a4df2b62 ckpt 2725: Made the ten-sources research resumable by any session/account: tools/resear
```
