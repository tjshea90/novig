# CHECKPOINT 2123 — read me first, then TASKS.md

**Written:** 2026-09-30T00:54:09Z · **tests:** all 2 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `70dfa3aa` (this checkpoint is the commit after it)

## Just done
E2/E3 code in and compiling: HistoricalCloses.kt (EspnCloses: scoreboard→gameOf→core odds close, devig, exact line only; NovigTradeCloses: index.json, per-ET-day trades.csv via Range binary search, 30-min pre-start VWAP per outcome, outcome from outcomeId or betUrl; CloseBackfill: due/retry 3h/give up 60d/closeFinal), TrackedBet closeFair/closeVia/closeNote/closeLookedAtMs/closeFinal, ClosingLine.closeOf priority captured > history > at-bet, wired into VM gradeAll + SettleWorker

## Do this next
tests with recorded payloads: save trimmed real ESPN core odds + a small trades.csv slice as test fixtures, MockWebServer for Range; then UI (sheet close source, CLV card sources), Diagnostics, RESEARCH.md §42

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  d95f8764 ckpt 2122: E1 research done in scratch (to write into RESEARCH.md §42): ESPN core odds
  85344378 ckpt 2121: Recorded Tj's request (find CLV from closing lines after the start / days la
  9be3e8a2 ckpt 2120: v0.25.0 (code 53) released and recorded; D1-D5 ticked
  52e3dbc6 ckpt 2119: pre-release: v0.25.0: true closing line value: Vigilant reads each bet's fai
  b76f82d6 ckpt 2118: D1-D4 ticked; RESEARCH.md §41; version 0.25.0 (code 53); two older tests re
  b1b9cd28 ckpt 2117: CLV app tests green (ClosingLineAppTest 7: alarm follows bets, paused captur
  d7b07ac1 ckpt 2116: D2-D4 code in: CLV card (own period chips + Hide outliers switch) replaces t
  2c00d584 ckpt 2115: D2 core written and compiling: ClosingLine (true close = pregame read within
  bf19dc84 ckpt 2114: Recorded Tj's request (true closing line value: find each bet's true close, 
  80a2c490 ckpt 2113: v0.24.0 (code 52) released and recorded; C1-C3 ticked
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
