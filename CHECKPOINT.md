# CHECKPOINT 4709 — read me first, then TASKS.md

**Written:** 2026-11-19T21:09:02Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `fa74d7d08` (this checkpoint is the commit after it)

## Just done
MK16: cap on parlays too likely to win (maxFair .85->.60) and on any one leg (maxLegFair .90), steppers on the maker page, test, study section 7; applied via rfq_set (maxFair live now); FULL suite green (4465)

## Do this next
no reset: maxLegFair arrives with the next job (6 h handover); watch status.rfq.declines for 'too likely to win'; MK14 follow-ups 00:40 / 14:00 UTC and coverage follow-up 14:30 UTC 11-20; ship the app (steppers) with the next release batch

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M research/INDEX.md

## Last ten checkpoints
```
  48627980a ckpt 4708: pre-release: MK15: Parlay hub (Engine > Parlays > My parlays): open and clo
  9398be95b ckpt 4707: pre-ship: MK15: Parlay hub (Engine > Parlays > My parlays): open and closed
  f3bcc56af ckpt 4706: MK15: parlay hub built (engine parlays.json + fills memory + seed, phone hu
  f76746de7 ckpt 4705: MK15 part 1: ParlayBook/FillBook/ParlayHub (data), engine keeps each fill's
  dbea1c1c9 ckpt 4704: MK14: LIVE capital hurdle liveMinReturn .03 (+ paper journaling), leg + EXE
  b6e0f5569 ckpt 4703: MK13 verified live: standing read 6/6 confirmed, 1 socket no reconnects, ex
  a78018e3f ckpt 4702: MK13c: per-outcome collateral cap (maxLegCollateral $30, LIVE), executions 
  6b65df2ba ckpt 4701: WATCH 2026-11-19: engine healthy, no action
  66b41859f ckpt 4700: MK13b: docs audit written (research/novig_rfq_docs_audit_2026-11-19.md), NO
  422e9a32a ckpt 4699: MK13a: Novig docs audit fixes in the RFQ maker: reconnect rules (RfqReconne
```

(9 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
