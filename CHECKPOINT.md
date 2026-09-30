# CHECKPOINT 2219 — read me first, then TASKS.md

**Written:** 2026-09-30T22:20:56Z · **tests:** all 3 fast checks green
**Branch:** `ccr-01f0c1f9-xy73ag` · **builds on:** `aa28a1c8` (this checkpoint is the commit after it)

## Just done
pre-release: v0.36.1: no more lag and crash switching tabs mid-scan (the screen takes a scan's progress and the API meters a few times a second instead of on every Novig read, and rebuilds the +EV feed off the main thread only when it changed); Diagnostics keeps a crash's stack and Android's record of how the app last ended (versionCode 65, v0.36.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.36.1), then run: bash tools/record-release.sh v0.36.1 65 "v0.36.1: no more lag and crash switching tabs mid-scan (the screen takes a scan's progress and the API meters a few times a second instead of on every Novig read, and rebuilds the +EV feed off the main thread only when it changed); Diagnostics keeps a crash's stack and Android's record of how the app last ended"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  aa28a1c8 ckpt 2218: X1/X2: scan and usage mirrors throttled (350 ms / 1 s), feed rebuilt only on
  809c349d ckpt 2217: Logged Tj's crash report (switching tabs during a Vigilant scan: laggy then 
  45b6058b ckpt 2216: v0.36.0 (code 64) released and recorded: W1-W4 done
  af9b6d8a ckpt 2215: pre-release: v0.36.0: Diagnostics fixes from Tj's first report (no false ala
  bccf7b7e ckpt 2214: W2/W3: Diagnostics false alarms fixed (busy source, backup source, spent key
  b50f4dbc ckpt 2213: Logged Tj's pasted v0.35.0 Diagnostics as TASKS.md W1-W4
  d857f629 ckpt 2212: v0.35.0 (code 63) released and recorded: V1-V4 done
  03124699 ckpt 2211: pre-release: v0.35.0: Bet sheet takes any typed amount and opens at the wall
  7830ace6 ckpt 2210: Fixed a real race in ApiBettingController.placer() (two plans on two threads
  47c2a353 ckpt 2209: V3 done: Diagnostics health checks (FAIL/WARN/OK with evidence and code), ac
```
