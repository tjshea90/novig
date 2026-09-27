# CHECKPOINT 439 — read me first, then TASKS.md

**Written:** 2026-09-27T00:04:47Z · **tests:** all 1 fast checks green
**Branch:** `claude/cno-scanner-enhancements-tj52i1` · **builds on:** `3cf811b` (this checkpoint is the commit after it)

## Just done
pre-release: v0.15.1: floating widget resizes with two fingers (spread/pinch, and slide to move) or its four corner handles, drags by its frame or taller top bar; the cut-off corner grip is gone; 'CNO error' fixed (cancelled reads were counted as errors; CNO/ESPN replies read off the main thread) and the widget says what went wrong; the pull-to-refresh arrow no longer sticks; CNO tab shows when it last read. 409 tests (versionCode 21, v0.15.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.15.1), then run: bash tools/record-release.sh v0.15.1 21 "v0.15.1: floating widget resizes with two fingers (spread/pinch, and slide to move) or its four corner handles, drags by its frame or taller top bar; the cut-off corner grip is gone; 'CNO error' fixed (cancelled reads were counted as errors; CNO/ESPN replies read off the main thread) and the widget says what went wrong; the pull-to-refresh arrow no longer sticks; CNO tab shows when it last read. 409 tests"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  3cf811b ckpt 438: J5 tests green: 409 tests 0 failed; FloatingWidget handles touches in dispatch
  a79a462 ckpt 437: J1-J4, J6 done: framed widget with corner handles, pinch/spread + two-finger m
  4cfc2e5 ckpt 436: Logged Tj's request: pinch/corner resize, easier move, cut-off bottom-right gr
  cd1f331 ckpt 435: SHIPPED v0.15.0 (code 20): floating CNO widget, placed bets, teams, green chec
  0d37c74 ckpt 434: pre-release: v0.15.0: CNO widget you can touch (floating over Novig): Up/Down 
  df36b8b ckpt 433: H9 full test: fixes + forced full rerun 388 tests 0 failed 3 skipped exit 0; v
  d43058c ckpt 432: full test: ESPN 403s a Vigilant User-Agent (live) -> OkHttp default UA; capped
  fd5cd79 ckpt 431: full test: lane fixes (cancelled books read no longer a 2-min 'failure'; re-pr
  b1d5d39 ckpt 430: H8 docs: RESEARCH.md §20, NOVIG_API.md §9.1 (Novig app links), BRIEF.md CNO 
  196922d ckpt 429: H4-H7 done: floating widget (340x290dp default) + tests green (ScreenshotTest 
```
