# CHECKPOINT 2312 — read me first, then TASKS.md

**Written:** 2026-10-02T05:14:55Z · **tests:** all 3 fast checks green
**Branch:** `ccr-ea5bf769-s5xd3t` · **builds on:** `412fd980` (this checkpoint is the commit after it)

## Just done
pre-release: v0.44.1: a bet under your minimum edge says so and where to change it (it said 'The edge is gone' at +2.4% EV) (versionCode 80, v0.44.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.44.1), then run: bash tools/record-release.sh v0.44.1 80 "v0.44.1: a bet under your minimum edge says so and where to change it (it said 'The edge is gone' at +2.4% EV)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  412fd980 ckpt 2311: AP1: a positive edge under Tj's minimum now says so and names the setting (w
  6a52c87b ckpt 2310: v0.44.0 released and recorded (AO1-AO6 done): lag fix, ParlayAPI freshness, 
  07ce6e1a ckpt 2309: pre-release: v0.44.0: smoother +EV list during scans, ParlayAPI quotes dated
  d8695cbe ckpt 2308: AO1 full tests done: floor 1604/23/0 with screenshots, 97 PNGs looked at, 3 
  df8ee1b3 ckpt 2307: RESEARCH §63, PARLAY_API/NOVIG_API notes, TASKS AO2-AO5 ticked, KeyActions 
  2128b83f ckpt 2306: Diagnostics review fixes: onTrimMemory drops boards past the freshness limit
  c9ed0bd8 ckpt 2305: AO2 lag found and fixed: root built a new ApiBetActions for the STATIC Local
  354f4eb9 ckpt 2304: Live feed: opened at the first plan, ONE bulk subscribe of the unread lines 
  d3062e41 ckpt 2303: AO3 part: ParlayAPI quotes dated by the book's verified-at last_update, not 
  5e414948 ckpt 2302: wrote Tj's full-tests + lag + 9-min odds + APIs request into TASKS.md as AO1
```
