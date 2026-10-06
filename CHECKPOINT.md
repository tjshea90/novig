# CHECKPOINT 2624 — read me first, then TASKS.md

**Written:** 2026-10-06T14:36:26Z · **tests:** all 4 fast checks green
**Branch:** `ccr-b67c8afa-h14i94` · **builds on:** `093c6306` (this checkpoint is the commit after it)

## Just done
pre-release: v0.70.2: the Diagnostics file always says whether the live burst recorder and the real-money trader are on or off (versionCode 120, v0.70.2)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.70.2), then run: bash tools/record-release.sh v0.70.2 120 "v0.70.2: the Diagnostics file always says whether the live burst recorder and the real-money trader are on or off"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  e6ba9e93 ckpt 2623: built plan.py (wave planner, selftest) + save_agent.sh (bank one agent's res
  2b3b86ab ckpt 2622: Tj's 14:14Z instruction recorded as CY1-CY3 in TASKS.md (max 3 agents in fli
  8a4d5a5a ckpt 2620: 4th container: files re-sent and re-extracted into scratchpad v0701 (loader 
  93c73318 ckpt 2619: Tj re-sent the two v0.70.1 files; extracted (loader matches the phone) and l
  1bc152c5 ckpt 2618: container lost again (uploads, scratchpad, analyst results all gone): CX1/CX
  a67088fd ckpt 2617: launched 14 background analyst agents (phase 1 of the v0.70.1 analysis) in t
  6d988e7b ckpt 2616: v0.70.1 analysis resumed in a NEW container (old workflow lost): files were 
  0039a333 ckpt 2615: Tj sent two files (v0.70.1 diagnostics + scan study, no words): data extract
  1909ebb8 ckpt 2614: v0.70.1 RELEASED and recorded (v0.70.0 too); RESEARCH 95/96 and NOVIG_API 20
  d4810416 ckpt 2613: pre-release: v0.70.1: the burst trader is judged league by league (a league 
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
