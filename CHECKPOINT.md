# CHECKPOINT 2571 — read me first, then TASKS.md

**Written:** 2026-10-05T18:43:01Z · **tests:** all 3 fast checks green
**Branch:** `ccr-fef46304-swa9m4` · **builds on:** `93cffb3f` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.66.0: API audit against the providers' own docs (RESEARCH §90): Novig batch place/cancel for bids (one request per 256, falls back to single orders), Kalshi alternate host fallback, PropLine 30 s board reuse in Pinnacle only; no waste found; PropLine Hobby not needed for grading

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  93cffb3f ckpt 2570: docs: RESEARCH 90.8 built/left, NOVIG_API batch use, CO1-CO7 ticked
  02e9bbac ckpt 2569: Kalshi alternate-host fallback (404/410/unknown host -> external-api.kalshi.
  b9293edb ckpt 2568: Novig batch place/cancel in the bid desk (client flag, fallback to singles, 
  42f8cff7 ckpt 2567: RESEARCH §90 written: API audit (docs vs code, ranking, PropLine buy answer
  46e07514 ckpt 2566: pre-release: v0.65.0: Pinnacle only (Settings › Scanning and the Auto-bet 
  b3a705c9 ckpt 2565: pre-ship: v0.65.0: Pinnacle only (Settings › Scanning and the Auto-bet tab
  235f67db ckpt 2564: Pinnacle only: +EV feed banner, health checks, mid-pass STOP test (kills the
  4d46c4c5 ckpt 2563: CI1-CI3 ticked; RESEARCH 88.5 written
  8def1f67 ckpt 2562: CI3: Pinnacle only in Diagnostics (section, scanner/auto-bet lines), scan st
  db5ac025 ckpt 2561: CI2 UI: Pinnacle only switch + age chips in Settings › Scanning and the Au
```
