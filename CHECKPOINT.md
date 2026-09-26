# CHECKPOINT 431 — read me first, then TASKS.md

**Written:** 2026-09-26T20:50:31Z · **tests:** all 1 fast checks green
**Branch:** `claude/cno-scanner-enhancements-tj52i1` · **builds on:** `9081777` (this checkpoint is the commit after it)

## Just done
full test: lane fixes (cancelled books read no longer a 2-min 'failure'; re-priced list no longer restarts lanes; teams lane ignores re-order; widget reopens full, not as a bubble) + 2 failing-first tests

## Do this next
continue full sweep: engine, data/novig, data/reference, data/scanner, data/keys, app screens

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  b1d5d39 ckpt 430: H8 docs: RESEARCH.md §20, NOVIG_API.md §9.1 (Novig app links), BRIEF.md CNO 
  196922d ckpt 429: H4-H7 done: floating widget (340x290dp default) + tests green (ScreenshotTest 
  5ba9dfd ckpt 428: CnoWatch (data) runs CNO+lanes only while watched (CnoWatchTest); MiniWindowTe
  ac53aad ckpt 427: H5-H7 first build compiles: VM watcher set (tab/pip/overlay) runs CNO watch + 
  f9ae87e ckpt 426: H1-H3 data layer: PlacedBets, CnoBooks agreement (SPLIT) + CnoFeed.keepBooksFr
  3ba6ab1 ckpt 425: H0 research done: PiP can't take touches -> overlay widget; CNO deeplink = Nov
  b55d6be ckpt 424: Recorded v0.14.1 release (G3 done); logged Tj's 6-item CNO widget request as H
  775ebaa ckpt 423: pre-release: v0.14.1: the mini window shows each pick clearly (names were draw
  7e9707d ckpt 422: G1-G2: mini window pick names readable (own Surface + full-contrast bold name;
  27d02b7 ckpt 421: Logged Tj's request: the mini window doesn't show what the pick is (screenshot
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
