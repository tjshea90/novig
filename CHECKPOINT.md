# CHECKPOINT 4712 — read me first, then TASKS.md

**Written:** 2026-11-19T22:10:20Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `6cbc0f66e` (this checkpoint is the commit after it)

## Just done
MK17: tonight's options (only today, longshots <=25% with 3% premium, wallet as the collateral limit), safe-edge floor, same-day hold, wallet status, app options card + steppers, positive-EV pass written; FULL suite green (4472)

## Do this next
post board, ONE engine reset (confirm startedAtMs + status.rfq.walletCash), ship v1.0.142 (versionCode 482) + release.yml, set the Tonight options in config (SHADOW), tell Tj to tap Live when funded

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  920ebd129 ckpt 4711: MK17 part 1: onlyToday / longshotsOnly / walletCollateral in the pricer and
  6ff8b5530 ckpt 4710: MK10 21:51: 4 of 4 settled parlays were buyer hits (adverse selection watch
  a4d62498c ckpt 4709: MK16: cap on parlays too likely to win (maxFair .85->.60) and on any one le
  48627980a ckpt 4708: pre-release: MK15: Parlay hub (Engine > Parlays > My parlays): open and clo
  9398be95b ckpt 4707: pre-ship: MK15: Parlay hub (Engine > Parlays > My parlays): open and closed
  f3bcc56af ckpt 4706: MK15: parlay hub built (engine parlays.json + fills memory + seed, phone hu
  f76746de7 ckpt 4705: MK15 part 1: ParlayBook/FillBook/ParlayHub (data), engine keeps each fill's
  dbea1c1c9 ckpt 4704: MK14: LIVE capital hurdle liveMinReturn .03 (+ paper journaling), leg + EXE
  b6e0f5569 ckpt 4703: MK13 verified live: standing read 6/6 confirmed, 1 socket no reconnects, ex
  a78018e3f ckpt 4702: MK13c: per-outcome collateral cap (maxLegCollateral $30, LIVE), executions 
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
