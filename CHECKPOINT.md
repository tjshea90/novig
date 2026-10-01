# CHECKPOINT 2259 — read me first, then TASKS.md

**Written:** 2026-10-01T12:51:32Z · **tests:** all 3 fast checks green
**Branch:** `ccr-650fc40f-rtt4er` · **builds on:** `f67520f9` (this checkpoint is the commit after it)

## Just done
wrote Tj's longest-odds request into TASKS.md as AE1-AE3 (answer: Kelly scales stakes down with odds but doesn't cap longshots)

## Do this next
AE1: ScanSettings.autoBetMaxOdds + AUTO_BET_MAX_ODDS_CHOICES; AutoBet.Rules.maxOdds + judge(american); BetLimits.maxOdds + planner refusal; AutoBetUi chips; tests

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  6e96526f ckpt 2258: v0.39.1 released and recorded (AD1-AD4 done): priced-market reuse + stale-bo
  f185e97f ckpt 2257: pre-release: v0.39.1: the out-of-memory crash, second layer: a partial scan 
  f86a8554 ckpt 2256: pre-ship: v0.39.1: the out-of-memory crash, second layer: a partial scan res
  53d0a76d ckpt 2255: AD1+AD2: priced-market reuse in FairMemo/Pricing (same book object, bankroll
  f19090eb ckpt 2254: v0.39.0 released and recorded; ticked AB5/AC4
  68df33bd ckpt 2253: wrote Tj's follow-up crash request into TASKS.md as AD1-AD4 while v0.39.0's 
  9e07456a ckpt 2252: pre-release: v0.39.0: auto-bet (off by default) places CrazyNinjaOdds' bets 
  ff15ba49 ckpt 2251: AC3 done (in-flight marker, mutation-checked); RESEARCH §51 corrected + §5
  5c584048 ckpt 2250: AC2: OOM fixes - largeHeap, MemoryGuard (graceful scan stop at 90% after GC,
  fef12530 ckpt 2249: AC: logged Tj's v0.38.0 Diagnostics (OOM crash, Vigilant CLV, live API bets 
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
