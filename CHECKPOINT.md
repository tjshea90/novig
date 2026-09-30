# CHECKPOINT 2128 — read me first, then TASKS.md

**Written:** 2026-09-30T01:08:04Z · **tests:** all 2 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `286d721d` (this checkpoint is the commit after it)

## Just done
pre-release: v0.26.0: closing lines are found after the game starts, even days later, when the phone was off: ESPN's closing odds for moneylines, spreads and totals right after the start, and Novig's own last trades before the start for every market (props too) the next morning; the CLV card counts closes by source and each bet shows its close (versionCode 54, v0.26.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.26.0), then run: bash tools/record-release.sh v0.26.0 54 "v0.26.0: closing lines are found after the game starts, even days later, when the phone was off: ESPN's closing odds for moneylines, spreads and totals right after the start, and Novig's own last trades before the start for every market (props too) the next morning; the CLV card counts closes by source and each bet shows its close"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  286d721d ckpt 2127: F0 done: Wi-Fi gate removed, rule written into BRIEF.md + CLAUDE.md
  ba5aac97 ckpt 2126: Recorded Tj's mid-turn request as F0-F4 (research 4 sources, implement what 
  f9dfe964 ckpt 2125: E1-E3 ticked; RESEARCH.md §42; NOVIG_API §10 verified note; CLV card text 
  45dfb5b8 ckpt 2124: E2/E3: HistoricalClosesTest 10 green on recorded ESPN/Novig payloads (fixtur
  7822290d ckpt 2123: E2/E3 code in and compiling: HistoricalCloses.kt (EspnCloses: scoreboard→g
  d95f8764 ckpt 2122: E1 research done in scratch (to write into RESEARCH.md §42): ESPN core odds
  85344378 ckpt 2121: Recorded Tj's request (find CLV from closing lines after the start / days la
  9be3e8a2 ckpt 2120: v0.25.0 (code 53) released and recorded; D1-D5 ticked
  52e3dbc6 ckpt 2119: pre-release: v0.25.0: true closing line value: Vigilant reads each bet's fai
  b76f82d6 ckpt 2118: D1-D4 ticked; RESEARCH.md §41; version 0.25.0 (code 53); two older tests re
```
