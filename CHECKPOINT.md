# CHECKPOINT 4050 — read me first, then TASKS.md

**Written:** 2026-10-23T14:43:06Z · **tests:** all 9 fast checks green
**Branch:** `claude/optimistic-curie-gxcjqr` · **builds on:** `28dc12f35` (this checkpoint is the commit after it)

## Just done
pre-release: CH73: master switch Run on GitHub (default on; off = phone mode, engine off, trading OFF); audit shows the phone stands down while the engine drives (versionCode 401, v1.0.61)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.61), then run: bash tools/record-release.sh v1.0.61 401 "CH73: master switch Run on GitHub (default on; off = phone mode, engine off, trading OFF); audit shows the phone stands down while the engine drives"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  28dc12f35 ckpt 4049: pre-ship: CH73: master switch Run on GitHub (default on; off = phone mode, 
  10930e032 ckpt 4048: CH73 audit written
  1b82d88ce ckpt 4047: ENGINEER 2026-10-23: CH57b spend re-read, no change
  c11513e95 ckpt 4046: CH73 part 1: master switch 'Run on GitHub' (off=phone mode, trading OFF), t
  80643f85e ckpt 4045: chat: engine bids set to 800% wallet, margin 0.02
  2861e87d8 ckpt 4044: chat session 2026-10-22/23: shipped v1.0.49-1.0.60 (Kalshi ticker, no phone
  900772ee5 ckpt 4043: CH68d confirmed by Tj: Kalshi bets went through
  2e23b0dbd ckpt 4042: pre-release: CH71a an order that finds nobody selling (or a refusal that sa
  86dc43e69 ckpt 4041: bump 1.0.60
  e588f8528 ckpt 4040: CH71a: NotFilled/gone order results take the row off the list
```
