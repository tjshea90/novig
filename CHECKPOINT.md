# CHECKPOINT 2291 — read me first, then TASKS.md

**Written:** 2026-10-02T01:02:39Z · **tests:** all 3 fast checks green
**Branch:** `ccr-650fc40f-rtt4er` · **builds on:** `cc2c18b7` (this checkpoint is the commit after it)

## Just done
AM: recorder, advisor, file, share built and tested; FQN where/pathOf/short edits applied but not yet compiled; per-step cycle timing not yet added

## Do this next
compile, fix EventLog/Advisor/DiagnosticsFile tests for FQN where, add cycle.step timings in AutoScan.kt, mutation checks, RESEARCH 61, bump 0.43.0 code 78, ship

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/build.gradle.kts
     M app/src/main/kotlin/com/tjshea/vigilant/app/Advisor.kt
     M app/src/main/kotlin/com/tjshea/vigilant/app/AutoScanService.kt
     M app/src/main/kotlin/com/tjshea/vigilant/app/Diagnostics.kt
     M app/src/main/kotlin/com/tjshea/vigilant/app/DiagnosticsFile.kt
     M app/src/test/kotlin/com/tjshea/vigilant/app/KeepAwakeServiceTest.kt
     M app/src/test/kotlin/com/tjshea/vigilant/app/SharpConfirmAppTest.kt
     M data/src/main/kotlin/com/tjshea/vigilant/data/diag/DiagHistory.kt
     M data/src/main/kotlin/com/tjshea/vigilant/data/diag/EventLog.kt
     M data/src/main/kotlin/com/tjshea/vigilant/data/diag/NetInterceptor.kt
     M data/src/main/kotlin/com/tjshea/vigilant/data/diag/NetStats.kt
     M data/src/main/kotlin/com/tjshea/vigilant/data/diag/ProblemLog.kt
     M data/src/test/kotlin/com/tjshea/vigilant/data/diag/ProblemLogTest.kt
    ?? app/src/test/kotlin/com/tjshea/vigilant/app/AdvisorTest.kt
    ?? app/src/test/kotlin/com/tjshea/vigilant/app/DiagnosticsFileTest.kt
    ?? app/src/test/kotlin/com/tjshea/vigilant/app/DiagnosticsShareTest.kt
    ?? app/src/test/kotlin/com/tjshea/vigilant/app/DiagnosticsUiTest.kt
    ?? data/src/test/kotlin/com/tjshea/vigilant/data/diag/EventLogTest.kt
    ?? data/src/test/kotlin/com/tjshea/vigilant/data/diag/LogcatAndTrendTest.kt
    ?? data/src/test/kotlin/com/tjshea/vigilant/data/diag/NetStatsTest.kt

## Last ten checkpoints
```
  cc2c18b7 ckpt 2290: AM5-AM6 built: Advisor (findings), DiagnosticsFile (read-me, findings, trend
  f5b3a473 ckpt 2289: AM2-AM4 data layer: EventLog, NetStats+NetInterceptor+NetShape, PerfStats, L
  ed803bea ckpt 2288: wrote Tj's smart-diagnostics-file request into TASKS.md as AM1-AM6
  6913a468 ckpt 2287: v0.42.0 released and recorded: reopen resets auto-bet/background scan; sharp
  ab65fde9 ckpt 2286: pre-release: v0.42.0: auto-bet and background auto-scan switch themselves of
  4badc0d5 ckpt 2285: mutation checks done (sharp rules, feeds, gate, wiring, LaunchReset); docs R
  c510206c ckpt 2284: AL1 built (LaunchReset); AL4 built: SharpConfirm rules, SharpBooks feeds, Sh
  5d05a967 ckpt 2283: wrote Tj's reopen-resets and sharp-book confirmation request into TASKS.md a
  6aa592f3 ckpt 2282: v0.41.0 released and recorded: Keep awake for auto-scan/auto-bet with the sc
  ce28edc3 ckpt 2281: pre-release: v0.41.0: Keep awake keeps background auto-scan and auto-bet on 
```
