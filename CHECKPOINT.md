# CHECKPOINT 2260 — read me first, then TASKS.md

**Written:** 2026-10-01T13:04:34Z · **tests:** all 3 fast checks green
**Branch:** `ccr-650fc40f-rtt4er` · **builds on:** `ec61dba2` (this checkpoint is the commit after it)

## Just done
pre-release: v0.39.2: auto-bet gets a longest-odds limit (Settings › Betting › Auto-bet: +100 … +300, No limit, or typed), checked on the bet's price and again on Novig's order book just before the order; favorites always pass; Kelly stakes shrink with longer odds but never capped them (versionCode 71, v0.39.2)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.39.2), then run: bash tools/record-release.sh v0.39.2 71 "v0.39.2: auto-bet gets a longest-odds limit (Settings › Betting › Auto-bet: +100 … +300, No limit, or typed), checked on the bet's price and again on Novig's order book just before the order; favorites always pass; Kelly stakes shrink with longer odds but never capped them"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  947c80cf ckpt 2259: wrote Tj's longest-odds request into TASKS.md as AE1-AE3 (answer: Kelly scal
  6e96526f ckpt 2258: v0.39.1 released and recorded (AD1-AD4 done): priced-market reuse + stale-bo
  f185e97f ckpt 2257: pre-release: v0.39.1: the out-of-memory crash, second layer: a partial scan 
  f86a8554 ckpt 2256: pre-ship: v0.39.1: the out-of-memory crash, second layer: a partial scan res
  53d0a76d ckpt 2255: AD1+AD2: priced-market reuse in FairMemo/Pricing (same book object, bankroll
  f19090eb ckpt 2254: v0.39.0 released and recorded; ticked AB5/AC4
  68df33bd ckpt 2253: wrote Tj's follow-up crash request into TASKS.md as AD1-AD4 while v0.39.0's 
  9e07456a ckpt 2252: pre-release: v0.39.0: auto-bet (off by default) places CrazyNinjaOdds' bets 
  ff15ba49 ckpt 2251: AC3 done (in-flight marker, mutation-checked); RESEARCH §51 corrected + §5
  5c584048 ckpt 2250: AC2: OOM fixes - largeHeap, MemoryGuard (graceful scan stop at 90% after GC,
```

(13 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
