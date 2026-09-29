# CHECKPOINT 2100 — read me first, then TASKS.md

**Written:** 2026-09-29T19:45:57Z · **tests:** all 2 fast checks green
**Branch:** `ccr-538c37db-finzj9` · **builds on:** `37df2065` (this checkpoint is the commit after it)

## Just done
pre-release: v0.22.0: the widget opens only from its button; Settings in seven tabs; Tracker, +EV, Games and CNO keep their tabs and filters pinned while you scroll; Check odds now asks only for the market families your bets are on; Diagnostics shows a Runway (which API allowance lasts) and what the last scan and Check odds now cost each API (versionCode 50, v0.22.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.22.0), then run: bash tools/record-release.sh v0.22.0 50 "v0.22.0: the widget opens only from its button; Settings in seven tabs; Tracker, +EV, Games and CNO keep their tabs and filters pinned while you scroll; Check odds now asks only for the market families your bets are on; Diagnostics shows a Runway (which API allowance lasts) and what the last scan and Check odds now cost each API"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  37df2065 ckpt 2099: A1-A5 ticked; RESEARCH.md §40; version bumped to 0.22.0 (code 50); MGM sett
  014c514c ckpt 2098: A5: tabs and filters pinned on the Tracker (Stats|Bets, Open/Settled/All, co
  502aff59 ckpt 2097: A4: Settings is seven top tabs (Scan, CNO & widget, Fair odds, +EV feed, Bet
  a6f40324 ckpt 2096: A1/A3: bets-only pass asks only the bets' market families (BetsScope.familie
  0da2dec5 ckpt 2095: A2: widget only opens from its button: miniWindow default off + schema-10 mi
  ba13d8ea ckpt 2094: Recorded Tj's request (optimize from the two Diagnostics reports; widget mus
  5b176f1d ckpt 2093: v0.21.3 (code 49) released and recorded; Z1-Z4 ticked
  523dda5e ckpt 2092: pre-release: v0.21.3: CNO only now sleeps Vigilant in the background too (au
  7b903e8f ckpt 2091: Z1-Z3: CNO only sleeps Vigilant in the background (autoScansVigilant/Cno, ru
  e91d74ed ckpt 2090: Recorded Tj's request (grading check for API bets, what to show me to optimi
```
