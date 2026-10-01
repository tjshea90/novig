# CHECKPOINT 2258 — read me first, then TASKS.md

**Written:** 2026-10-01T07:02:00Z · **tests:** all 3 fast checks green
**Branch:** `ccr-650fc40f-rtt4er` · **builds on:** `242b820e` (this checkpoint is the commit after it)

## Just done
v0.39.1 released and recorded (AD1-AD4 done): priced-market reuse + stale-board drop for the OOM

## Do this next
Nothing open for this request. Next Diagnostics from Tj: read the Memory block (heap, result size, boards/books held) to see whether the OOM is gone; if boards dominate, intern RefBookMarket strings. Auto-bet still unverified live (first run with $1 stakes).

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  f185e97f ckpt 2257: pre-release: v0.39.1: the out-of-memory crash, second layer: a partial scan 
  f86a8554 ckpt 2256: pre-ship: v0.39.1: the out-of-memory crash, second layer: a partial scan res
  53d0a76d ckpt 2255: AD1+AD2: priced-market reuse in FairMemo/Pricing (same book object, bankroll
  f19090eb ckpt 2254: v0.39.0 released and recorded; ticked AB5/AC4
  68df33bd ckpt 2253: wrote Tj's follow-up crash request into TASKS.md as AD1-AD4 while v0.39.0's 
  9e07456a ckpt 2252: pre-release: v0.39.0: auto-bet (off by default) places CrazyNinjaOdds' bets 
  ff15ba49 ckpt 2251: AC3 done (in-flight marker, mutation-checked); RESEARCH §51 corrected + §5
  5c584048 ckpt 2250: AC2: OOM fixes - largeHeap, MemoryGuard (graceful scan stop at 90% after GC,
  fef12530 ckpt 2249: AC: logged Tj's v0.38.0 Diagnostics (OOM crash, Vigilant CLV, live API bets 
  34c41f20 ckpt 2248: pre-release: v0.39.0: auto-bet (off by default): places CrazyNinjaOdds' bets
```
