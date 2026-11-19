# CHECKPOINT 4716 — read me first, then TASKS.md

**Written:** 2026-11-19T22:39:07Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `b61cb3d8b` (this checkpoint is the commit after it)

## Just done
MK17c: collateral hold 120 s -> 45 s after the auction closes (fills come 6-29 s after the quote), a fill stays counted 75 s until the balance read shows it; why-so-little diagnosis logged; FULL suite green (4473)

## Do this next
graceful engine reset (engine_set --reset) so the live maker quotes more; then verify quotesSent rate and cash-short declines drop; MK14 follow-ups 00:40/14:00 UTC, coverage 14:30 UTC

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  1c29ee335 ckpt 4715: pre-release: MK17b: Parlays tiles show the whole wallet and the cash free t
  328ab2f64 ckpt 4714: pre-ship: MK17b: Parlays tiles show the whole wallet and the cash free to p
  8abd10054 ckpt 4713: pre-release: MK17: parlay options on the Maker page: only parlays that fini
  890d33726 ckpt 4712: MK17: tonight's options (only today, longshots <=25% with 3% premium, walle
  920ebd129 ckpt 4711: MK17 part 1: onlyToday / longshotsOnly / walletCollateral in the pricer and
  6ff8b5530 ckpt 4710: MK10 21:51: 4 of 4 settled parlays were buyer hits (adverse selection watch
  a4d62498c ckpt 4709: MK16: cap on parlays too likely to win (maxFair .85->.60) and on any one le
  48627980a ckpt 4708: pre-release: MK15: Parlay hub (Engine > Parlays > My parlays): open and clo
  9398be95b ckpt 4707: pre-ship: MK15: Parlay hub (Engine > Parlays > My parlays): open and closed
  f3bcc56af ckpt 4706: MK15: parlay hub built (engine parlays.json + fills memory + seed, phone hu
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
