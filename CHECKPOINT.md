# CHECKPOINT 2257 — read me first, then TASKS.md

**Written:** 2026-10-01T06:51:10Z · **tests:** all 3 fast checks green
**Branch:** `ccr-650fc40f-rtt4er` · **builds on:** `f86a8554` (this checkpoint is the commit after it)

## Just done
pre-release: v0.39.1: the out-of-memory crash, second layer: a partial scan result prices only the Novig books that changed (40 partials of a 1,200-market scan allocate 23 MB, not 212), and a scan lets go of the boards of leagues you turned off (versionCode 70, v0.39.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.39.1), then run: bash tools/record-release.sh v0.39.1 70 "v0.39.1: the out-of-memory crash, second layer: a partial scan result prices only the Novig books that changed (40 partials of a 1,200-market scan allocate 23 MB, not 212), and a scan lets go of the boards of leagues you turned off"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  f86a8554 ckpt 2256: pre-ship: v0.39.1: the out-of-memory crash, second layer: a partial scan res
  53d0a76d ckpt 2255: AD1+AD2: priced-market reuse in FairMemo/Pricing (same book object, bankroll
  f19090eb ckpt 2254: v0.39.0 released and recorded; ticked AB5/AC4
  68df33bd ckpt 2253: wrote Tj's follow-up crash request into TASKS.md as AD1-AD4 while v0.39.0's 
  9e07456a ckpt 2252: pre-release: v0.39.0: auto-bet (off by default) places CrazyNinjaOdds' bets 
  ff15ba49 ckpt 2251: AC3 done (in-flight marker, mutation-checked); RESEARCH §51 corrected + §5
  5c584048 ckpt 2250: AC2: OOM fixes - largeHeap, MemoryGuard (graceful scan stop at 90% after GC,
  fef12530 ckpt 2249: AC: logged Tj's v0.38.0 Diagnostics (OOM crash, Vigilant CLV, live API bets 
  34c41f20 ckpt 2248: pre-release: v0.39.0: auto-bet (off by default): places CrazyNinjaOdds' bets
  daa35c59 ckpt 2247: pre-ship: v0.39.0: auto-bet (off by default): places CrazyNinjaOdds' bets th
```
