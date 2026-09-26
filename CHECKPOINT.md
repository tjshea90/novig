# CHECKPOINT 434 — read me first, then TASKS.md

**Written:** 2026-09-26T21:00:25Z · **tests:** all 1 fast checks green
**Branch:** `claude/cno-scanner-enhancements-tj52i1` · **builds on:** `df36b8b` (this checkpoint is the commit after it)

## Just done
pre-release: v0.15.0: CNO widget you can touch (floating over Novig): Up/Down always at the bottom, tap a bet to open it in Novig's bet slip, ✓ to mark it placed (hidden for good, Undo), hold for every book; player teams like D. Schultz (HOU); green ✓ when 3+ books agree; CNO reads only while its tab or a widget is on screen. Full test: 388 tests (versionCode 20, v0.15.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.15.0), then run: bash tools/record-release.sh v0.15.0 20 "v0.15.0: CNO widget you can touch (floating over Novig): Up/Down always at the bottom, tap a bet to open it in Novig's bet slip, ✓ to mark it placed (hidden for good, Undo), hold for every book; player teams like D. Schultz (HOU); green ✓ when 3+ books agree; CNO reads only while its tab or a widget is on screen. Full test: 388 tests"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  df36b8b ckpt 433: H9 full test: fixes + forced full rerun 388 tests 0 failed 3 skipped exit 0; v
  d43058c ckpt 432: full test: ESPN 403s a Vigilant User-Agent (live) -> OkHttp default UA; capped
  fd5cd79 ckpt 431: full test: lane fixes (cancelled books read no longer a 2-min 'failure'; re-pr
  b1d5d39 ckpt 430: H8 docs: RESEARCH.md §20, NOVIG_API.md §9.1 (Novig app links), BRIEF.md CNO 
  196922d ckpt 429: H4-H7 done: floating widget (340x290dp default) + tests green (ScreenshotTest 
  5ba9dfd ckpt 428: CnoWatch (data) runs CNO+lanes only while watched (CnoWatchTest); MiniWindowTe
  ac53aad ckpt 427: H5-H7 first build compiles: VM watcher set (tab/pip/overlay) runs CNO watch + 
  f9ae87e ckpt 426: H1-H3 data layer: PlacedBets, CnoBooks agreement (SPLIT) + CnoFeed.keepBooksFr
  3ba6ab1 ckpt 425: H0 research done: PiP can't take touches -> overlay widget; CNO deeplink = Nov
  b55d6be ckpt 424: Recorded v0.14.1 release (G3 done); logged Tj's 6-item CNO widget request as H
```
