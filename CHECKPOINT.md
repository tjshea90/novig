# CHECKPOINT 2247 — read me first, then TASKS.md

**Written:** 2026-10-01T06:15:34Z · **tests:** all 3 fast checks green
**Branch:** `ccr-650fc40f-rtt4er` · **builds on:** `b13efc31` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.39.0: auto-bet (off by default): places CrazyNinjaOdds' bets through Novig's API from the Vigilant wallet inside the background CNO scan, with your criteria (books agreeing, minimum edge, books pricing both sides, 1/8-1/4-1/2 Kelly or $1 or typed stake, most per bet, the scan's own interval), pregame only, stops when the wallet is empty, every bet tracked

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M .github/workflows/release.yml
     M CHECKPOINT.md

## Last ten checkpoints
```
  b13efc31 ckpt 2246: AB4 done: floor 1,360 green with screenshots, RESEARCH §51, BRIEF locked ru
  e265908c ckpt 2245: AB3 done: Auto-bet Settings card, Diagnostics + health checks, Tracker tag, 
  94c563d6 ckpt 2244: AB2 done: AutoBettor wired into the cycle with 11 mutation-checked safeguard
  5bafe868 ckpt 2243: AB1 done: auto-bet settings, AutoBet rules (criteria, Kelly stakes, caps, wa
  15ca1df8 ckpt 2242: AB: wrote Tj's auto-bet request into TASKS.md as AB1-AB5 with how each point
  20aa70d3 ckpt 2241: v0.38.0 (code 68) released and recorded: AA1-AA5 done (auto-scan 15 s/30 s/1
  687eeb9c ckpt 2240: pre-release: v0.38.0: background auto-scan every 15 sec, 30 sec, 1 min or 3 
  31ff8423 ckpt 2239: AA1-AA4 done: interval in seconds, fast-cycle safety, CLV real closes; floor
  c08d3611 ckpt 2238: AA2/AA3: at-bet no longer a close, final read ~110 s before start (needsFina
  b86607f3 ckpt 2237: AA1+AA2 core: autoScanSeconds (15/30/60/180 s + 5-40 min, schema 12 migratio
```
