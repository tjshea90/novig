# CHECKPOINT 2131 — read me first, then TASKS.md

**Written:** 2026-09-30T01:24:39Z · **tests:** all 2 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `4a8d986b` (this checkpoint is the commit after it)

## Just done
F2b ParlayCloses compiles: Pinnacle prop closes (daily file, UTC+ET date), game lines (daysFrom<=30), credit-limit 403 = Later; CloseSource.active so a keyless ParlayAPI isn't asked or counted

## Do this next
tests: ParlayCloses parse + HTTP, OddsFeed.PARLAY client, CloseBackfill active/order; RESEARCH §43; v0.27.0 code 55; ship

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  40fda8c4 ckpt 2130: v0.26.0 (code 54) released and recorded; E1-E4 ticked; F2a (ParlayAPI as a f
  5fe7de23 ckpt 2129: F2a in progress: OddsFeed (ODDS_API/PARLAY) in TheOddsApiClient + OddsApiPro
  25f31ac8 ckpt 2128: pre-release: v0.26.0: closing lines are found after the game starts, even da
  286d721d ckpt 2127: F0 done: Wi-Fi gate removed, rule written into BRIEF.md + CLAUDE.md
  ba5aac97 ckpt 2126: Recorded Tj's mid-turn request as F0-F4 (research 4 sources, implement what 
  f9dfe964 ckpt 2125: E1-E3 ticked; RESEARCH.md §42; NOVIG_API §10 verified note; CLV card text 
  45dfb5b8 ckpt 2124: E2/E3: HistoricalClosesTest 10 green on recorded ESPN/Novig payloads (fixtur
  7822290d ckpt 2123: E2/E3 code in and compiling: HistoricalCloses.kt (EspnCloses: scoreboard→g
  d95f8764 ckpt 2122: E1 research done in scratch (to write into RESEARCH.md §42): ESPN core odds
  85344378 ckpt 2121: Recorded Tj's request (find CLV from closing lines after the start / days la
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
