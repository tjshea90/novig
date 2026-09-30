# CHECKPOINT 2134 — read me first, then TASKS.md

**Written:** 2026-09-30T01:54:19Z · **tests:** all 2 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `6560ce54` (this checkpoint is the commit after it)

## Just done
Recorded Tj's 01:42Z request as G1-G6 (use ParlayAPI Starter fully, prioritize where better, fall back when gone, judge free tier, full test protocol, sleep when closed)

## Do this next
Finish F2 mid-change: wire ParlayCloses to shared ParlayAPI KeyPool in VigilantApp.kt, fix ParlayClosesTest to KeyPool, reserve test, compile+tests; then G1 research of Starter endpoints; then F1/F3 §43, G2-G6

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  21f454a8 ckpt 2133: MID-CHANGE (not yet compiled): KeyPool.execute(reserve) + OddsFeed.reserve (
  0613045f ckpt 2132: F2 tests green: ParlayClosesTest 7 (props file/game lines parse, HTTP+cache,
  89d19720 ckpt 2131: F2b ParlayCloses compiles: Pinnacle prop closes (daily file, UTC+ET date), g
  40fda8c4 ckpt 2130: v0.26.0 (code 54) released and recorded; E1-E4 ticked; F2a (ParlayAPI as a f
  5fe7de23 ckpt 2129: F2a in progress: OddsFeed (ODDS_API/PARLAY) in TheOddsApiClient + OddsApiPro
  25f31ac8 ckpt 2128: pre-release: v0.26.0: closing lines are found after the game starts, even da
  286d721d ckpt 2127: F0 done: Wi-Fi gate removed, rule written into BRIEF.md + CLAUDE.md
  ba5aac97 ckpt 2126: Recorded Tj's mid-turn request as F0-F4 (research 4 sources, implement what 
  f9dfe964 ckpt 2125: E1-E3 ticked; RESEARCH.md §42; NOVIG_API §10 verified note; CLV card text 
  45dfb5b8 ckpt 2124: E2/E3: HistoricalClosesTest 10 green on recorded ESPN/Novig payloads (fixtur
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
