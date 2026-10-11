# CHECKPOINT 3001 — read me first, then TASKS.md

**Written:** 2026-10-11T19:54:54Z · **tests:** all 4 fast checks green
**Branch:** `ccr-4a7a6b39-3ig8vu` · **builds on:** `82fec641c` (this checkpoint is the commit after it)

## Just done
TX filters, TY auto-bet recovery done in code; wrote TZ (no negative-EV orders anywhere)

## Do this next
TZ1 audit order choke points, TZ2 guard, tests, ship v0.89.25

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  0ed57d4f5 ckpt 3000: TW done in code (reasons per bet, 2% EV cap for 3.5*+); wrote TX (live-only
  ddef5d3c3 ckpt 2999: TW written (autobet 4-star not betting; rating needs >=2% EV for 3.5+)
  bae6652c0 ckpt 2998: pre-release: v0.89.24: Pause stops everything at once, live lane keeps rows
  e5fc694e6 ckpt 2997: pre-ship: v0.89.24: Pause stops everything at once, live lane keeps rows wh
  74b7d3bde ckpt 2996: v0.89.23 shipped+release triggered; TT written (pause must stop all scannin
  6a8c02eed ckpt 2995: pre-release: v0.89.23: Check odds now also reads Novig's own price for ever
  715db5975 ckpt 2994: pre-ship: v0.89.23: Check odds now also reads Novig's own price for every o
  ac8a1c95e ckpt 2993: v0.89.22 released and recorded (TQ, TR)
  0d9e145f2 ckpt 2992: v0.89.22 shipped to main, release.yml triggered (TQ live lane, TR check odd
  327196d38 ckpt 2991: pre-release: v0.89.22: real-time live lane (Pinnodds+Novig, 5 s rule), Chec
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
