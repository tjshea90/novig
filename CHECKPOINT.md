# CHECKPOINT 4713 — read me first, then TASKS.md

**Written:** 2026-11-19T22:13:37Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `4c7a83608` (this checkpoint is the commit after it)

## Just done
pre-release: MK17: parlay options on the Maker page: only parlays that finish today, longshots only (25% or less), the wallet as the collateral limit, a Tonight button, safety margins; plus the parlay hub (versionCode 482, v1.0.142)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.142), then run: bash tools/record-release.sh v1.0.142 482 "MK17: parlay options on the Maker page: only parlays that finish today, longshots only (25% or less), the wallet as the collateral limit, a Tonight button, safety margins; plus the parlay hub"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  890d33726 ckpt 4712: MK17: tonight's options (only today, longshots <=25% with 3% premium, walle
  920ebd129 ckpt 4711: MK17 part 1: onlyToday / longshotsOnly / walletCollateral in the pricer and
  6ff8b5530 ckpt 4710: MK10 21:51: 4 of 4 settled parlays were buyer hits (adverse selection watch
  a4d62498c ckpt 4709: MK16: cap on parlays too likely to win (maxFair .85->.60) and on any one le
  48627980a ckpt 4708: pre-release: MK15: Parlay hub (Engine > Parlays > My parlays): open and clo
  9398be95b ckpt 4707: pre-ship: MK15: Parlay hub (Engine > Parlays > My parlays): open and closed
  f3bcc56af ckpt 4706: MK15: parlay hub built (engine parlays.json + fills memory + seed, phone hu
  f76746de7 ckpt 4705: MK15 part 1: ParlayBook/FillBook/ParlayHub (data), engine keeps each fill's
  dbea1c1c9 ckpt 4704: MK14: LIVE capital hurdle liveMinReturn .03 (+ paper journaling), leg + EXE
  b6e0f5569 ckpt 4703: MK13 verified live: standing read 6/6 confirmed, 1 socket no reconnects, ex
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
