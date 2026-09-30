# CHECKPOINT 2122 — read me first, then TASKS.md

**Written:** 2026-09-30T00:48:39Z · **tests:** all 2 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `85344378` (this checkpoint is the commit after it)

## Just done
E1 research done in scratch (to write into RESEARCH.md §42): ESPN core odds keep DK/ESPN BET open/close ML+spread+total for finished games (2025 NFL, Jan 2026 NBA, MLB, NCAAF, MLS 3-way); ESPN props have lines only; Novig data.novig.com trades.csv per ET day (36MB, time-sorted, HTTP Range 206, outcomeId rows, price=cost/qty, published ~09:00Z next day, since 2026-08-03) gives Novig's close for any market; MNF check: Novig VWAP 0.634 vs DK devig 0.6225; Kalshi candlesticks keep settled game-winner history; Polymarket history empty for probe; Odds API historical is paid

## Do this next
read Scores.kt/BetGrader for ESPN event lookup reuse; implement EspnCloses + NovigTradeCloses + CloseBackfill (data), TrackedBet closeFair/closeVia/closeLookedAtMs/closeNote, ClosingLine priority, run in SettleWorker + app open + Grade now

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  85344378 ckpt 2121: Recorded Tj's request (find CLV from closing lines after the start / days la
  9be3e8a2 ckpt 2120: v0.25.0 (code 53) released and recorded; D1-D5 ticked
  52e3dbc6 ckpt 2119: pre-release: v0.25.0: true closing line value: Vigilant reads each bet's fai
  b76f82d6 ckpt 2118: D1-D4 ticked; RESEARCH.md §41; version 0.25.0 (code 53); two older tests re
  b1b9cd28 ckpt 2117: CLV app tests green (ClosingLineAppTest 7: alarm follows bets, paused captur
  d7b07ac1 ckpt 2116: D2-D4 code in: CLV card (own period chips + Hide outliers switch) replaces t
  2c00d584 ckpt 2115: D2 core written and compiling: ClosingLine (true close = pregame read within
  bf19dc84 ckpt 2114: Recorded Tj's request (true closing line value: find each bet's true close, 
  80a2c490 ckpt 2113: v0.24.0 (code 52) released and recorded; C1-C3 ticked
  c1a35dc8 ckpt 2112: pre-release: v0.24.0: the Tracker's Check odds now counter, pinned at the to
```
