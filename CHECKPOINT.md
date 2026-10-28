# CHECKPOINT 4308 — read me first, then TASKS.md

**Written:** 2026-10-28T23:23:56Z · **tests:** all 9 fast checks green
**Branch:** `ccr-f17ba379-0eynsd` · **builds on:** `6c0bbe481` (this checkpoint is the commit after it)

## Just done
pre-release: HR1: restore settled bets and bids from the GitHub backup or a history file (versionCode 430, v1.0.90)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.90), then run: bash tools/record-release.sh v1.0.90 430 "HR1: restore settled bets and bids from the GitHub backup or a history file"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  7017efa6e ckpt 4307: WATCH 2026-10-28: engine alive, trading key rejected by Novig
  e4376b04e ckpt 4306: ENGINEER 2026-10-28 watch only, engine healthy, no change
  26f9309b3 ckpt 4305: ANALYST 2026-10-28 skipped by rule, engine healthy
  b6d104c9f ckpt 4304: ENGINEER 2026-10-27 watch only, engine healthy, Novig maintenance lock, no 
  125aa3cb8 ckpt 4303: OFF1 done: v1.0.89 released
  910300bd7 ckpt 4302: pre-release: v1.0.89 OFF1: offline copy of engine status, orders, bids and 
  b26d538e4 ckpt 4301: pre-ship: v1.0.89 OFF1: offline copy of engine status, orders, bids and usa
  34ff76bc8 ckpt 4300: OFF1a: offline copy of engine status/orders/bids/usage
  fc3fa9058 ckpt 4299: ANALYST 2026-10-27 skipped by rule, engine healthy
  de3cbdf05 ckpt 4298: WATCH 2026-10-27 engine healthy, no change
```
