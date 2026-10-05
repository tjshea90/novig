# CHECKPOINT 2566 — read me first, then TASKS.md

**Written:** 2026-10-05T18:24:23Z · **tests:** all 3 fast checks green
**Branch:** `ccr-fef46304-swa9m4` · **builds on:** `b3a705c9` (this checkpoint is the commit after it)

## Just done
pre-release: v0.65.0: Pinnacle only (Settings › Scanning and the Auto-bet tab): Vigilant's scan prices Novig against Pinnacle's devigged price alone and reads nothing else (PinnWire, then pinnapi; PropLine or ParlayAPI only for a league those can't answer), auto-bet bets what beats it on a Pinnacle price re-read within your age limit, and Diagnostics, the scan study, the Tracker and the health checks track those bets apart (EV, CLV, profit) (versionCode 112, v0.65.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.65.0), then run: bash tools/record-release.sh v0.65.0 112 "v0.65.0: Pinnacle only (Settings › Scanning and the Auto-bet tab): Vigilant's scan prices Novig against Pinnacle's devigged price alone and reads nothing else (PinnWire, then pinnapi; PropLine or ParlayAPI only for a league those can't answer), auto-bet bets what beats it on a Pinnacle price re-read within your age limit, and Diagnostics, the scan study, the Tracker and the health checks track those bets apart (EV, CLV, profit)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  b3a705c9 ckpt 2565: pre-ship: v0.65.0: Pinnacle only (Settings › Scanning and the Auto-bet tab
  235f67db ckpt 2564: Pinnacle only: +EV feed banner, health checks, mid-pass STOP test (kills the
  4d46c4c5 ckpt 2563: CI1-CI3 ticked; RESEARCH 88.5 written
  8def1f67 ckpt 2562: CI3: Pinnacle only in Diagnostics (section, scanner/auto-bet lines), scan st
  db5ac025 ckpt 2561: CI2 UI: Pinnacle only switch + age chips in Settings › Scanning and the Au
  ce4ad21b ckpt 2560: CI2 core: PinnacleBackup + Pinnacle-only sources, PinnacleBet rules, AutoBet
  b409e741 ckpt 2559: pre-release: v0.64.0: STOP ALL kill switch (red bar on every tab, widget, no
  64708aec ckpt 2558: fixed a kill-switch race: the pause watcher now cancels bids with the kill s
  3ebe8403 ckpt 2557: wrote CO7 (does MatchWire help prop matching / save API usage) into TASKS.md
  5e20215c ckpt 2556: wrote Tj's API-audit request (CO1-CO6) into TASKS.md with the new work order
```
