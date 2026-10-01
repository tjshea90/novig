# CHECKPOINT 2250 — read me first, then TASKS.md

**Written:** 2026-10-01T06:27:16Z · **tests:** all 3 fast checks green
**Branch:** `ccr-650fc40f-rtt4er` · **builds on:** `cfd20888` (this checkpoint is the commit after it)

## Just done
AC2: OOM fixes - largeHeap, MemoryGuard (graceful scan stop at 90% after GC, trim caches at 75%), big plans publish partials at most every 2 s, soft fallback result, heap in Diagnostics + crash record; tests mutation-checked

## Do this next
AC3: auto-bet in-flight marker (halt before sending, clear on a definitive result), then full floor, release v0.39.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  fef12530 ckpt 2249: AC: logged Tj's v0.38.0 Diagnostics (OOM crash, Vigilant CLV, live API bets 
  34c41f20 ckpt 2248: pre-release: v0.39.0: auto-bet (off by default): places CrazyNinjaOdds' bets
  daa35c59 ckpt 2247: pre-ship: v0.39.0: auto-bet (off by default): places CrazyNinjaOdds' bets th
  b13efc31 ckpt 2246: AB4 done: floor 1,360 green with screenshots, RESEARCH §51, BRIEF locked ru
  e265908c ckpt 2245: AB3 done: Auto-bet Settings card, Diagnostics + health checks, Tracker tag, 
  94c563d6 ckpt 2244: AB2 done: AutoBettor wired into the cycle with 11 mutation-checked safeguard
  5bafe868 ckpt 2243: AB1 done: auto-bet settings, AutoBet rules (criteria, Kelly stakes, caps, wa
  15ca1df8 ckpt 2242: AB: wrote Tj's auto-bet request into TASKS.md as AB1-AB5 with how each point
  20aa70d3 ckpt 2241: v0.38.0 (code 68) released and recorded: AA1-AA5 done (auto-scan 15 s/30 s/1
  687eeb9c ckpt 2240: pre-release: v0.38.0: background auto-scan every 15 sec, 30 sec, 1 min or 3 
```

(10 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
