# CHECKPOINT 2129 — read me first, then TASKS.md

**Written:** 2026-09-30T01:18:37Z · **tests:** all 2 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `c7fb8774` (this checkpoint is the commit after it)

## Just done
F2a in progress: OddsFeed (ODDS_API/PARLAY) in TheOddsApiClient + OddsApiPropsSource, ApiProvider.PARLAY, QuotaPolicy.PARLAY, ScanSettings.useParlay + enabledSources, Scanner SOURCE_ORDER/requestKey, AppContainer parlayOdds/parlayProps in referenceSources, UiState parlayKeys, usage meter map, Settings Fair odds switch + key editor

## Do this next
compile; then ParlayCloses CloseSource (closing-lines.json props + /sports/{s}/closing-lines game lines, Pinnacle), tests, RESEARCH §43, release

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/src/main/kotlin/com/tjshea/vigilant/app/MainViewModel.kt
     M app/src/main/kotlin/com/tjshea/vigilant/app/ui/SettingsScreen.kt
     M app/src/main/kotlin/com/tjshea/vigilant/app/ui/UsageMeters.kt

## Last ten checkpoints
```
  25f31ac8 ckpt 2128: pre-release: v0.26.0: closing lines are found after the game starts, even da
  286d721d ckpt 2127: F0 done: Wi-Fi gate removed, rule written into BRIEF.md + CLAUDE.md
  ba5aac97 ckpt 2126: Recorded Tj's mid-turn request as F0-F4 (research 4 sources, implement what 
  f9dfe964 ckpt 2125: E1-E3 ticked; RESEARCH.md §42; NOVIG_API §10 verified note; CLV card text 
  45dfb5b8 ckpt 2124: E2/E3: HistoricalClosesTest 10 green on recorded ESPN/Novig payloads (fixtur
  7822290d ckpt 2123: E2/E3 code in and compiling: HistoricalCloses.kt (EspnCloses: scoreboard→g
  d95f8764 ckpt 2122: E1 research done in scratch (to write into RESEARCH.md §42): ESPN core odds
  85344378 ckpt 2121: Recorded Tj's request (find CLV from closing lines after the start / days la
  9be3e8a2 ckpt 2120: v0.25.0 (code 53) released and recorded; D1-D5 ticked
  52e3dbc6 ckpt 2119: pre-release: v0.25.0: true closing line value: Vigilant reads each bet's fai
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
