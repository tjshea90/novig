# CHECKPOINT 2560 — read me first, then TASKS.md

**Written:** 2026-10-05T17:55:29Z · **tests:** all 3 fast checks green
**Branch:** `ccr-fef46304-swa9m4` · **builds on:** `18e60315` (this checkpoint is the commit after it)

## Just done
CI2 core: PinnacleBackup + Pinnacle-only sources, PinnacleBet rules, AutoBettor.runPinnacle (sendOrder shared with run), scan-end trigger, AtBet tags, FairBasis case fix, tests (data + app)

## Do this next
CI2 UI: Settings switch + age chips + scannerNow gating; Tracker Pinnacle only chip; CI3 Diagnostics + study; then RESEARCH 88.5

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/src/main/kotlin/com/tjshea/vigilant/app/VigilantApp.kt
     M data/src/main/kotlin/com/tjshea/vigilant/data/novig/trading/PinnacleBet.kt
     M data/src/test/kotlin/com/tjshea/vigilant/data/novig/trading/PinnacleBetTest.kt

## Last ten checkpoints
```
  b409e741 ckpt 2559: pre-release: v0.64.0: STOP ALL kill switch (red bar on every tab, widget, no
  64708aec ckpt 2558: fixed a kill-switch race: the pause watcher now cancels bids with the kill s
  3ebe8403 ckpt 2557: wrote CO7 (does MatchWire help prop matching / save API usage) into TASKS.md
  5e20215c ckpt 2556: wrote Tj's API-audit request (CO1-CO6) into TASKS.md with the new work order
  336095bd ckpt 2555: CI2 started: pinnacleOnly settings + effective() + Scanner.refreshFair (data
  271f2d84 ckpt 2554: CK1-CK3 ticked: bid health checks, Diagnostics unlimited wording, fast-fill 
  29ec9d49 ckpt 2553: CN1 done (MatchWire is mapping-only, no prices/Pinnacle/Novig: no use); RESE
  b63b888a ckpt 2552: CK2 code: BidReport (rows, summary, fill lines), AtBet for maker fills, Diag
  1c7b3d90 ckpt 2551: CK1/CK3 code: sharp-anchored bid price + Kelly on the anchor, fill-time judg
  f3449ca6 ckpt 2550: CL1 done: kill switch (killed + derived paused, KillMarker second copy, Kill
```

(21 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
