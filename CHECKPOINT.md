# CHECKPOINT 4708 — read me first, then TASKS.md

**Written:** 2026-11-19T20:31:31Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `9398be95b` (this checkpoint is the commit after it)

## Just done
pre-release: MK15: Parlay hub (Engine > Parlays > My parlays): open and closed parlays with every leg's status, profit/loss, collateral, expected final time, what you keep if the buyers lose (versionCode 481, v1.0.141)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.141), then run: bash tools/record-release.sh v1.0.141 481 "MK15: Parlay hub (Engine > Parlays > My parlays): open and closed parlays with every leg's status, profit/loss, collateral, expected final time, what you keep if the buyers lose"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  9398be95b ckpt 4707: pre-ship: MK15: Parlay hub (Engine > Parlays > My parlays): open and closed
  f3bcc56af ckpt 4706: MK15: parlay hub built (engine parlays.json + fills memory + seed, phone hu
  f76746de7 ckpt 4705: MK15 part 1: ParlayBook/FillBook/ParlayHub (data), engine keeps each fill's
  dbea1c1c9 ckpt 4704: MK14: LIVE capital hurdle liveMinReturn .03 (+ paper journaling), leg + EXE
  b6e0f5569 ckpt 4703: MK13 verified live: standing read 6/6 confirmed, 1 socket no reconnects, ex
  a78018e3f ckpt 4702: MK13c: per-outcome collateral cap (maxLegCollateral $30, LIVE), executions 
  6b65df2ba ckpt 4701: WATCH 2026-11-19: engine healthy, no action
  66b41859f ckpt 4700: MK13b: docs audit written (research/novig_rfq_docs_audit_2026-11-19.md), NO
  422e9a32a ckpt 4699: MK13a: Novig docs audit fixes in the RFQ maker: reconnect rules (RfqReconne
  5ca44ed7e ckpt 4698: MK11 17:38: ladder lifts priced trades 0.5% -> 19% on paper
```
