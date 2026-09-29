# CHECKPOINT 2067 — read me first, then TASKS.md

**Written:** 2026-09-29T07:54:32Z · **tests:** all 2 fast checks green
**Branch:** `ccr-538c37db-finzj9` · **builds on:** `76c46f2d` (this checkpoint is the commit after it)

## Just done
pre-release: v0.20.2: Polymarket reads ~2x faster (13 s to 5.6 s live); the scan-timing line names the slowest fair-odds source; Test key now also times your key's live feed, limits and book reads and prints the numbers; Kalshi 429 handling kept safe (measured). Research: full Novig API docs (NOVIG_API.md 14) and seven third-party APIs (RESEARCH.md 36) (versionCode 45, v0.20.2)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.20.2), then run: bash tools/record-release.sh v0.20.2 45 "v0.20.2: Polymarket reads ~2x faster (13 s to 5.6 s live); the scan-timing line names the slowest fair-odds source; Test key now also times your key's live feed, limits and book reads and prints the numbers; Kalshi 429 handling kept safe (measured). Research: full Novig API docs (NOVIG_API.md 14) and seven third-party APIs (RESEARCH.md 36)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  76c46f2d ckpt 2066: floor green (839 passed, 19 live skipped, exit 0); probe limited to 10 marke
  37d09d09 ckpt 2065: V1-V7 done: RESEARCH.md §36 (Novig API answers, measured slowness, Kalshi 4
  0dfd9ca3 ckpt 2064: V1-V3 written up in NOVIG_API.md §14 (all 51 routes, subaccount-wallet find
  69da58b4 ckpt 2063: Recorded Tj's two research projects (Novig API in depth + 7 third-party APIs
  92fa8cfd ckpt 2062: U6 ticked: v0.20.1 released
  330c2463 ckpt 2061: v0.20.1 (code 44) released and recorded: CI green (run 36535348703), release
  234f1dac ckpt 2060: pre-release: v0.20.1: Check odds now is ~2x faster (3 pages at once, 500 ms 
  31614eb3 ckpt 2059: full floor green (833 passed, 19 live skipped, exit 0); DNP voids say Novig 
  2747b6a0 ckpt 2058: v0.20.1 (code 44) versions bumped; test-protocols map updated; LiveCnoGradab
  29458a10 ckpt 2057: U1-U5 done and ticked with evidence; RESEARCH §35 written; Check odds now a
```
