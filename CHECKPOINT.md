# CHECKPOINT 2797 — read me first, then TASKS.md

**Written:** 2026-10-09T18:04:05Z · **tests:** all 4 fast checks green
**Branch:** `ccr-1dbf7537-vyxqtx` · **builds on:** `4df92746` (this checkpoint is the commit after it)

## Just done
pre-release: v0.83.5: only-money notifications option, open bids on every notification, bids go up 10 at a time, SGO odds-age guard option (10/12/15/20 min), alternate rungs capped to save memory (versionCode 152, v0.83.5)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.83.5), then run: bash tools/record-release.sh v0.83.5 152 "v0.83.5: only-money notifications option, open bids on every notification, bids go up 10 at a time, SGO odds-age guard option (10/12/15/20 min), alternate rungs capped to save memory"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  4df92746 ckpt 2796: pre-ship: v0.83.5: only-money notifications option, open bids on every notif
  da564486 ckpt 2795: pre-release: v0.83.4: SGO bids keep posting: with SGO Pro on the background 
  5bfa9ee2 ckpt 2794: pre-ship: v0.83.4: SGO bids keep posting: with SGO Pro on the background Vig
  89bf0ab1 ckpt 2793: pre-release: v0.83.3: SGO Pro mode freshness: 10-minute guard for every game
  48eb86db ckpt 2792: pre-ship: v0.83.3: SGO Pro mode freshness: 10-minute guard for every game wh
  316c6d8a ckpt 2791: pre-release: v0.83.2: SportsGameOdds actually prices the scan (sgo was missi
  c565a9e5 ckpt 2790: pre-ship: v0.83.2: SportsGameOdds actually prices the scan (sgo was missing 
  7488534c ckpt 2789: pre-release: v0.83.1: SportsGameOdds re-check against the docs: book filter 
  237c3d7f ckpt 2788: pre-ship: v0.83.1: SportsGameOdds re-check against the docs: book filter (SG
  b8d4c306 ckpt 2787: v0.83.0 shipped; GitHub lab proven end to end (run 37906589603 wrote lab-dat
```
