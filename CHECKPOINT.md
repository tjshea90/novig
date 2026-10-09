# CHECKPOINT 2805 — read me first, then TASKS.md

**Written:** 2026-10-09T23:20:59Z · **tests:** all 4 fast checks green
**Branch:** `ccr-1dbf7537-vyxqtx` · **builds on:** `3f7fd00e` (this checkpoint is the commit after it)

## Just done
pre-release: v0.84.1: wide-quote guard: a book or exchange quote whose two sides add up to too much (book over 12%, exchange over 10%) is left out of every fair line, shown as wide in the books table; on by default (Settings > Fair odds) (versionCode 156, v0.84.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.84.1), then run: bash tools/record-release.sh v0.84.1 156 "v0.84.1: wide-quote guard: a book or exchange quote whose two sides add up to too much (book over 12%, exchange over 10%) is left out of every fair line, shown as wide in the books table; on by default (Settings > Fair odds)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  3f7fd00e ckpt 2804: pre-ship: v0.84.1: wide-quote guard: a book or exchange quote whose two side
  5d9b3616 ckpt 2803: pre-release: v0.84.0: OddsPapi v5 ready to switch on: keys (rotated), toggle
  903d944c ckpt 2802: OddsPapi data layer built and compiling (client, parser, markets, books, con
  1029e4c0 ckpt 2801: pre-release: v0.83.7: Pinnodds awake in the app again (it was only Claude's 
  5e983eb0 ckpt 2800: pre-ship: v0.83.7: Pinnodds awake in the app again (it was only Claude's ses
  59f603ba ckpt 2799: pre-release: v0.83.6: SGO mode wakes Pinnodds: its fresh Pinnacle board pric
  bde9d5f3 ckpt 2798: pre-ship: v0.83.6: SGO mode wakes Pinnodds: its fresh Pinnacle board prices 
  70ced87f ckpt 2797: pre-release: v0.83.5: only-money notifications option, open bids on every no
  4df92746 ckpt 2796: pre-ship: v0.83.5: only-money notifications option, open bids on every notif
  da564486 ckpt 2795: pre-release: v0.83.4: SGO bids keep posting: with SGO Pro on the background 
```
