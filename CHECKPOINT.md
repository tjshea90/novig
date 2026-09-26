# CHECKPOINT 437 — read me first, then TASKS.md

**Written:** 2026-09-26T23:59:47Z · **tests:** all 1 fast checks green
**Branch:** `claude/cno-scanner-enhancements-tj52i1` · **builds on:** `b31e210` (this checkpoint is the commit after it)

## Just done
J1-J4, J6 done: framed widget with corner handles, pinch/spread + two-finger move, frame/top-bar drag in screen px; cancelled CNO reads no longer errors, bodies off main thread, short error reasons; pull arrow always lets go; CNO status shows last read

## Do this next
J5: forced full regression, screenshots, bump v0.15.1 code 21, ship, release, link

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M RESEARCH.md
     M TASKS.md
     M app/src/main/kotlin/com/tjshea/vigilant/app/ui/SettingsScreen.kt

## Last ten checkpoints
```
  4cfc2e5 ckpt 436: Logged Tj's request: pinch/corner resize, easier move, cut-off bottom-right gr
  cd1f331 ckpt 435: SHIPPED v0.15.0 (code 20): floating CNO widget, placed bets, teams, green chec
  0d37c74 ckpt 434: pre-release: v0.15.0: CNO widget you can touch (floating over Novig): Up/Down 
  df36b8b ckpt 433: H9 full test: fixes + forced full rerun 388 tests 0 failed 3 skipped exit 0; v
  d43058c ckpt 432: full test: ESPN 403s a Vigilant User-Agent (live) -> OkHttp default UA; capped
  fd5cd79 ckpt 431: full test: lane fixes (cancelled books read no longer a 2-min 'failure'; re-pr
  b1d5d39 ckpt 430: H8 docs: RESEARCH.md §20, NOVIG_API.md §9.1 (Novig app links), BRIEF.md CNO 
  196922d ckpt 429: H4-H7 done: floating widget (340x290dp default) + tests green (ScreenshotTest 
  5ba9dfd ckpt 428: CnoWatch (data) runs CNO+lanes only while watched (CnoWatchTest); MiniWindowTe
  ac53aad ckpt 427: H5-H7 first build compiles: VM watcher set (tab/pip/overlay) runs CNO watch + 
```

(16 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
