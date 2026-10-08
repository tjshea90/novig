# CHECKPOINT 2751 — read me first, then TASKS.md

**Written:** 2026-10-08T07:25:20Z · **tests:** all 4 fast checks green
**Branch:** `claude/auto-bid-optimization-api-kdjbe4` · **builds on:** `6bff6cfe` (this checkpoint is the commit after it)

## Just done
pre-release: v0.78.0: long-run saver for bids (lean background scan for Quick & likely, 8 min pace while every game is far off), far games' bids ranked last, bid funnel by time to start/side/league in Diagnostics (versionCode 139, v0.78.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.78.0), then run: bash tools/record-release.sh v0.78.0 139 "v0.78.0: long-run saver for bids (lean background scan for Quick & likely, 8 min pace while every game is far off), far games' bids ranked last, bid funnel by time to start/side/league in Diagnostics"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6bff6cfe ckpt 2750: v0.78.0 version bump; floor green (2663+1 tests); MakerUiTest for the saver 
  e0f7b556 ckpt 2749: RESEARCH.md §119 written (analysis for BA1-BA3, build notes)
  684a73b6 ckpt 2748: BA: long-run saver built (LongRunBids lean scan + 8 min far pace + far-last 
  41f46ab0 ckpt 2747: Finished the ten-sources rapid-feed research inline (RESEARCH.md §115, DO1-
  6ddff6df ckpt 2746: pre-release: v0.77.0: Pinnacle only removed entirely; Pinnodds live pregame 
  80dd594c ckpt 2745: pre-ship: v0.77.0: Pinnacle only removed entirely; Pinnodds live pregame ste
  5c280e8b ckpt 2744: PZ1 done: 'Pinnacle only' removed entirely (setting, scanner mode, auto-bet 
  e1c9e841 ckpt 2743: Researched Novig post-score pause vs Pinnodds lag trade and burst (RESEARCH.
  df9febd3 ckpt 2742: pre-release: v0.76.3: Pinnodds live Stale orders trigger (ask still at Pinna
  828922f5 ckpt 2741: pre-ship: v0.76.3: Pinnodds live Stale orders trigger (ask still at Pinnacle
```
