# CHECKPOINT 2249 — read me first, then TASKS.md

**Written:** 2026-10-01T06:18:44Z · **tests:** all 3 fast checks green
**Branch:** `ccr-650fc40f-rtt4er` · **builds on:** `2aaf242f` (this checkpoint is the commit after it)

## Just done
AC: logged Tj's v0.38.0 Diagnostics (OOM crash, Vigilant CLV, live API bets evidence) as AC1-AC4; v0.39.0 release held until AC2/AC3

## Do this next
AC2: find what holds memory in a no-limit scan (ScanResult, RefSnapshots, books maps, caches), largeHeap, heap in Diagnostics/crash record; AC3: in-flight halt marker

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  34c41f20 ckpt 2248: pre-release: v0.39.0: auto-bet (off by default): places CrazyNinjaOdds' bets
  daa35c59 ckpt 2247: pre-ship: v0.39.0: auto-bet (off by default): places CrazyNinjaOdds' bets th
  b13efc31 ckpt 2246: AB4 done: floor 1,360 green with screenshots, RESEARCH §51, BRIEF locked ru
  e265908c ckpt 2245: AB3 done: Auto-bet Settings card, Diagnostics + health checks, Tracker tag, 
  94c563d6 ckpt 2244: AB2 done: AutoBettor wired into the cycle with 11 mutation-checked safeguard
  5bafe868 ckpt 2243: AB1 done: auto-bet settings, AutoBet rules (criteria, Kelly stakes, caps, wa
  15ca1df8 ckpt 2242: AB: wrote Tj's auto-bet request into TASKS.md as AB1-AB5 with how each point
  20aa70d3 ckpt 2241: v0.38.0 (code 68) released and recorded: AA1-AA5 done (auto-scan 15 s/30 s/1
  687eeb9c ckpt 2240: pre-release: v0.38.0: background auto-scan every 15 sec, 30 sec, 1 min or 3 
  31ff8423 ckpt 2239: AA1-AA4 done: interval in seconds, fast-cycle safety, CLV real closes; floor
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
