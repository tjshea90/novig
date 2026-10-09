# CHECKPOINT 2799 — read me first, then TASKS.md

**Written:** 2026-10-09T20:15:59Z · **tests:** all 4 fast checks green
**Branch:** `ccr-1dbf7537-vyxqtx` · **builds on:** `bde9d5f3` (this checkpoint is the commit after it)

## Just done
pre-release: v0.83.6: SGO mode wakes Pinnodds: its fresh Pinnacle board prices every scan, bid and EV before any other Pinnacle feed (fallback to PinnWire/pinnapi); off = dormant as before (versionCode 153, v0.83.6)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.83.6), then run: bash tools/record-release.sh v0.83.6 153 "v0.83.6: SGO mode wakes Pinnodds: its fresh Pinnacle board prices every scan, bid and EV before any other Pinnacle feed (fallback to PinnWire/pinnapi); off = dormant as before"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  bde9d5f3 ckpt 2798: pre-ship: v0.83.6: SGO mode wakes Pinnodds: its fresh Pinnacle board prices 
  70ced87f ckpt 2797: pre-release: v0.83.5: only-money notifications option, open bids on every no
  4df92746 ckpt 2796: pre-ship: v0.83.5: only-money notifications option, open bids on every notif
  da564486 ckpt 2795: pre-release: v0.83.4: SGO bids keep posting: with SGO Pro on the background 
  5bfa9ee2 ckpt 2794: pre-ship: v0.83.4: SGO bids keep posting: with SGO Pro on the background Vig
  89bf0ab1 ckpt 2793: pre-release: v0.83.3: SGO Pro mode freshness: 10-minute guard for every game
  48eb86db ckpt 2792: pre-ship: v0.83.3: SGO Pro mode freshness: 10-minute guard for every game wh
  316c6d8a ckpt 2791: pre-release: v0.83.2: SportsGameOdds actually prices the scan (sgo was missi
  c565a9e5 ckpt 2790: pre-ship: v0.83.2: SportsGameOdds actually prices the scan (sgo was missing 
  7488534c ckpt 2789: pre-release: v0.83.1: SportsGameOdds re-check against the docs: book filter 
```
