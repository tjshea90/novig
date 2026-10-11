# CHECKPOINT 2997 — read me first, then TASKS.md

**Written:** 2026-10-11T19:38:41Z · **tests:** all 4 fast checks green
**Branch:** `ccr-4a7a6b39-3ig8vu` · **builds on:** `27c50693d` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.89.24: Pause stops everything at once, live lane keeps rows while both sockets are healthy and shows exact ages, live bets only fill at the listed odds

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M RESEARCH.md

## Last ten checkpoints
```
  74b7d3bde ckpt 2996: v0.89.23 shipped+release triggered; TT written (pause must stop all scannin
  6a8c02eed ckpt 2995: pre-release: v0.89.23: Check odds now also reads Novig's own price for ever
  715db5975 ckpt 2994: pre-ship: v0.89.23: Check odds now also reads Novig's own price for every o
  ac8a1c95e ckpt 2993: v0.89.22 released and recorded (TQ, TR)
  0d9e145f2 ckpt 2992: v0.89.22 shipped to main, release.yml triggered (TQ live lane, TR check odd
  327196d38 ckpt 2991: pre-release: v0.89.22: real-time live lane (Pinnodds+Novig, 5 s rule), Chec
  2789b3561 ckpt 2990: pre-ship: v0.89.22: real-time live lane (Pinnodds+Novig, 5 s rule), Check o
  e8dc299d2 ckpt 2989: pre-ship: v0.89.22: real-time live lane (Pinnodds+Novig, 5 s rule), Check o
  0b4f6e441 ckpt 2988: TQ done in code+tests (live lane, manual live bets re-read fair); wrote TR 
  457a2e91f ckpt 2987: TQ: live lane built (LiveScan.kt, runner scanPass, VM merge, Feed banner, l
```

(12 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
