# CHECKPOINT 2092 — read me first, then TASKS.md

**Written:** 2026-09-29T18:36:02Z · **tests:** all 2 fast checks green
**Branch:** `ccr-538c37db-finzj9` · **builds on:** `7b903e8f` (this checkpoint is the commit after it)

## Just done
pre-release: v0.21.3: CNO only now sleeps Vigilant in the background too (auto-scan Both no longer runs Vigilant's scan when the scanner is on CNO only, and the scan and pricing entry points refuse), Vigilant only likewise for CNO; Settings > Diagnostics: a copy-able report of settings, last scan timing, API usage, background scan and Tracker, and a Grading check that shows what Novig's ledger and positions say about each API bet beside how the Tracker graded it (versionCode 49, v0.21.3)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.21.3), then run: bash tools/record-release.sh v0.21.3 49 "v0.21.3: CNO only now sleeps Vigilant in the background too (auto-scan Both no longer runs Vigilant's scan when the scanner is on CNO only, and the scan and pricing entry points refuse), Vigilant only likewise for CNO; Settings > Diagnostics: a copy-able report of settings, last scan timing, API usage, background scan and Tracker, and a Grading check that shows what Novig's ledger and positions say about each API bet beside how the Tracker graded it"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  7b903e8f ckpt 2091: Z1-Z3: CNO only sleeps Vigilant in the background (autoScansVigilant/Cno, ru
  e91d74ed ckpt 2090: Recorded Tj's request (grading check for API bets, what to show me to optimi
  9f71aed4 ckpt 2089: v0.21.2 (code 48) released and recorded; Y1-Y3 ticked
  ec0c2cc9 ckpt 2088: pre-release: v0.21.2: fix for the first real API bets: Novig refused both or
  cbe4faa2 ckpt 2087: Y1/Y2: first real API orders refused for clientId format (vigilant- prefix);
  5984e7b7 ckpt 2086: v0.21.1 (code 47) released and recorded; X1-X5 ticked
  55cdc46b ckpt 2085: pre-release: v0.21.1: Check odds now updates the EV of every open bet, Vigil
  7d4c504e ckpt 2084: X2-X4 ticked with their tests; RESEARCH.md §38.3 (built, live 10/10 priced 
  a080d3fb ckpt 2083: X2d/X3/X4 built and tested: bets-only pricing pass wired into Check odds now
  e3d39422 ckpt 2082: X2d/X3/X4 code: BetRecheck.Report/Plan count the Vigilant pricing pass, chec
```
