# CHECKPOINT 2252 — read me first, then TASKS.md

**Written:** 2026-10-01T06:31:00Z · **tests:** all 3 fast checks green
**Branch:** `ccr-650fc40f-rtt4er` · **builds on:** `ff15ba49` (this checkpoint is the commit after it)

## Just done
pre-release: v0.39.0: auto-bet (off by default) places CrazyNinjaOdds' bets through Novig's API from the Vigilant wallet inside the background CNO scan, with your criteria, pregame only, wallet-aware, tracked; plus the out-of-memory crash fix (bigger heap, a heap guard that ends a scan gracefully, big scans publish less often, memory in Diagnostics) (versionCode 69, v0.39.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.39.0), then run: bash tools/record-release.sh v0.39.0 69 "v0.39.0: auto-bet (off by default) places CrazyNinjaOdds' bets through Novig's API from the Vigilant wallet inside the background CNO scan, with your criteria, pregame only, wallet-aware, tracked; plus the out-of-memory crash fix (bigger heap, a heap guard that ends a scan gracefully, big scans publish less often, memory in Diagnostics)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  ff15ba49 ckpt 2251: AC3 done (in-flight marker, mutation-checked); RESEARCH §51 corrected + §5
  5c584048 ckpt 2250: AC2: OOM fixes - largeHeap, MemoryGuard (graceful scan stop at 90% after GC,
  fef12530 ckpt 2249: AC: logged Tj's v0.38.0 Diagnostics (OOM crash, Vigilant CLV, live API bets 
  34c41f20 ckpt 2248: pre-release: v0.39.0: auto-bet (off by default): places CrazyNinjaOdds' bets
  daa35c59 ckpt 2247: pre-ship: v0.39.0: auto-bet (off by default): places CrazyNinjaOdds' bets th
  b13efc31 ckpt 2246: AB4 done: floor 1,360 green with screenshots, RESEARCH §51, BRIEF locked ru
  e265908c ckpt 2245: AB3 done: Auto-bet Settings card, Diagnostics + health checks, Tracker tag, 
  94c563d6 ckpt 2244: AB2 done: AutoBettor wired into the cycle with 11 mutation-checked safeguard
  5bafe868 ckpt 2243: AB1 done: auto-bet settings, AutoBet rules (criteria, Kelly stakes, caps, wa
  15ca1df8 ckpt 2242: AB: wrote Tj's auto-bet request into TASKS.md as AB1-AB5 with how each point
```
