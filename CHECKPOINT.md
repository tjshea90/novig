# CHECKPOINT 2097 — read me first, then TASKS.md

**Written:** 2026-09-29T19:29:56Z · **tests:** all 2 fast checks green
**Branch:** `ccr-538c37db-finzj9` · **builds on:** `2913086f` (this checkpoint is the commit after it)

## Just done
A4: Settings is seven top tabs (Scan, CNO & widget, Fair odds, +EV feed, Betting, Usage & keys, Tools; CNO only drops the three Vigilant pages), tab row outside the scrolling page so it is sticky, choice survives rotation, each page opens at its top; existing Settings tests moved to their tabs; SettingsTabsTest (6) green

## Do this next
A5 sticky top navigation on the Tracker (Stats|Bets + Open/Settled/All + Sort + Scanner) and any other screen with a top filter row; then RESEARCH.md §40, TASKS ticks, full test, sweep, ship v0.22.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  a6f40324 ckpt 2096: A1/A3: bets-only pass asks only the bets' market families (BetsScope.familie
  0da2dec5 ckpt 2095: A2: widget only opens from its button: miniWindow default off + schema-10 mi
  ba13d8ea ckpt 2094: Recorded Tj's request (optimize from the two Diagnostics reports; widget mus
  5b176f1d ckpt 2093: v0.21.3 (code 49) released and recorded; Z1-Z4 ticked
  523dda5e ckpt 2092: pre-release: v0.21.3: CNO only now sleeps Vigilant in the background too (au
  7b903e8f ckpt 2091: Z1-Z3: CNO only sleeps Vigilant in the background (autoScansVigilant/Cno, ru
  e91d74ed ckpt 2090: Recorded Tj's request (grading check for API bets, what to show me to optimi
  9f71aed4 ckpt 2089: v0.21.2 (code 48) released and recorded; Y1-Y3 ticked
  ec0c2cc9 ckpt 2088: pre-release: v0.21.2: fix for the first real API bets: Novig refused both or
  cbe4faa2 ckpt 2087: Y1/Y2: first real API orders refused for clientId format (vigilant- prefix);
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
