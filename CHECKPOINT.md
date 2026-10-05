# CHECKPOINT 2565 — read me first, then TASKS.md

**Written:** 2026-10-05T18:22:05Z · **tests:** all 3 fast checks green
**Branch:** `ccr-fef46304-swa9m4` · **builds on:** `cf3a65bc` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.65.0: Pinnacle only (Settings › Scanning and the Auto-bet tab): Vigilant's scan prices Novig against Pinnacle's devigged price alone and reads nothing else (PinnWire, then pinnapi; PropLine or ParlayAPI only for a league those can't answer), auto-bet bets what beats it on a Pinnacle price re-read within your age limit, and Diagnostics, the scan study, the Tracker and the health checks track those bets apart (EV, CLV, profit)

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  235f67db ckpt 2564: Pinnacle only: +EV feed banner, health checks, mid-pass STOP test (kills the
  4d46c4c5 ckpt 2563: CI1-CI3 ticked; RESEARCH 88.5 written
  8def1f67 ckpt 2562: CI3: Pinnacle only in Diagnostics (section, scanner/auto-bet lines), scan st
  db5ac025 ckpt 2561: CI2 UI: Pinnacle only switch + age chips in Settings › Scanning and the Au
  ce4ad21b ckpt 2560: CI2 core: PinnacleBackup + Pinnacle-only sources, PinnacleBet rules, AutoBet
  b409e741 ckpt 2559: pre-release: v0.64.0: STOP ALL kill switch (red bar on every tab, widget, no
  64708aec ckpt 2558: fixed a kill-switch race: the pause watcher now cancels bids with the kill s
  3ebe8403 ckpt 2557: wrote CO7 (does MatchWire help prop matching / save API usage) into TASKS.md
  5e20215c ckpt 2556: wrote Tj's API-audit request (CO1-CO6) into TASKS.md with the new work order
  336095bd ckpt 2555: CI2 started: pinnacleOnly settings + effective() + Scanner.refreshFair (data
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
