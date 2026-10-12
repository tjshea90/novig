# CHECKPOINT 3060 — read me first, then TASKS.md

**Written:** 2026-10-12T06:55:59Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `2b3323c8e` (this checkpoint is the commit after it)

## Just done
pre-release: v0.90.1: BoltOdds odds socket reads binary frames and subscribes without a greeting, Test key v2 (multi-book snapshot, scores sockets), real market names, preseason sports (versionCode 222, v0.90.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.90.1), then run: bash tools/record-release.sh v0.90.1 222 "v0.90.1: BoltOdds odds socket reads binary frames and subscribes without a greeting, Test key v2 (multi-book snapshot, scores sockets), real market names, preseason sports"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  2b3323c8e ckpt 3059: pre-ship: v0.90.1: BoltOdds odds socket reads binary frames and subscribes 
  d0a167b80 ckpt 3058: TBP4 done: BoltKeyTest v2 (closes the app's sockets, snapshot of the sport 
  3fa23b9e5 ckpt 3057: TBP1-TBP3 done: BoltOddsStream reads binary frames (BoltFrames utf8/gzip/zl
  189399b0e ckpt 3056: BoltOdds sample #1 read: 34 books (no Pinnacle/Circa/bet365; 9 of Tj's book
  3794ec48b ckpt 3055: v0.90.0 released and recorded: BoltOdds Pro (websocket lines + props, Test 
  a9bc66a3c ckpt 3054: pre-release: v0.90.0: BoltOdds Pro (websocket lines + props, Test key sampl
  d01978332 ckpt 3053: pre-ship: v0.90.0: BoltOdds Pro (websocket lines + props, Test key sample),
  298a71e29 ckpt 3052: TBO: docs written (BOLTODDS_API.md §7, RESEARCH.md §136, CLAUDE.md BoltOd
  e568abb49 ckpt 3051: TBO3 (app wiring): ApiEntry.BOLTODDS, VigilantApp client/feed/sources/boltA
  855ff31d5 ckpt 3050: TBO3 (data side, part 1): data/.../boltodds/ Models, Parser, Markets, Books
```
