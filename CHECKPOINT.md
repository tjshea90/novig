# CHECKPOINT 2124 — read me first, then TASKS.md

**Written:** 2026-09-30T00:58:23Z · **tests:** all 2 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `6d1ca59c` (this checkpoint is the commit after it)

## Just done
E2/E3: HistoricalClosesTest 10 green on recorded ESPN/Novig payloads (fixtures espn-odds-*.json, novig-trades-mnf.csv); CLV card + sheet show the close's source; sheet shows close/CLV for started+settled bets and why none yet; Diagnostics backfill lines

## Do this next
full floor, fix wording-dependent tests (ClosingLineAppTest counts line), add UI/Diagnostics tests for sources, RESEARCH.md §42, NOVIG_API.md §10 note, version 0.26.0 code 54, ship

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  7822290d ckpt 2123: E2/E3 code in and compiling: HistoricalCloses.kt (EspnCloses: scoreboard→g
  d95f8764 ckpt 2122: E1 research done in scratch (to write into RESEARCH.md §42): ESPN core odds
  85344378 ckpt 2121: Recorded Tj's request (find CLV from closing lines after the start / days la
  9be3e8a2 ckpt 2120: v0.25.0 (code 53) released and recorded; D1-D5 ticked
  52e3dbc6 ckpt 2119: pre-release: v0.25.0: true closing line value: Vigilant reads each bet's fai
  b76f82d6 ckpt 2118: D1-D4 ticked; RESEARCH.md §41; version 0.25.0 (code 53); two older tests re
  b1b9cd28 ckpt 2117: CLV app tests green (ClosingLineAppTest 7: alarm follows bets, paused captur
  d7b07ac1 ckpt 2116: D2-D4 code in: CLV card (own period chips + Hide outliers switch) replaces t
  2c00d584 ckpt 2115: D2 core written and compiling: ClosingLine (true close = pregame read within
  bf19dc84 ckpt 2114: Recorded Tj's request (true closing line value: find each bet's true close, 
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
