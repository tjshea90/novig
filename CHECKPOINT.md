# CHECKPOINT 3002 — read me first, then TASKS.md

**Written:** 2026-10-11T20:09:56Z · **tests:** all 4 fast checks green
**Branch:** `ccr-4a7a6b39-3ig8vu` · **builds on:** `04ea28c3c` (this checkpoint is the commit after it)

## Just done
pre-release: v0.89.25: no negative-EV order anywhere, a sharp book that disagrees vetoes, auto-bet clears a lost-order halt by itself, live-only filter and Stars sort, why-not on CNO cards, 3.5 stars need +2% EV (versionCode 210, v0.89.25)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.89.25), then run: bash tools/record-release.sh v0.89.25 210 "v0.89.25: no negative-EV order anywhere, a sharp book that disagrees vetoes, auto-bet clears a lost-order halt by itself, live-only filter and Stars sort, why-not on CNO cards, 3.5 stars need +2% EV"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  9355f70e5 ckpt 3001: TX filters, TY auto-bet recovery done in code; wrote TZ (no negative-EV ord
  0ed57d4f5 ckpt 3000: TW done in code (reasons per bet, 2% EV cap for 3.5*+); wrote TX (live-only
  ddef5d3c3 ckpt 2999: TW written (autobet 4-star not betting; rating needs >=2% EV for 3.5+)
  bae6652c0 ckpt 2998: pre-release: v0.89.24: Pause stops everything at once, live lane keeps rows
  e5fc694e6 ckpt 2997: pre-ship: v0.89.24: Pause stops everything at once, live lane keeps rows wh
  74b7d3bde ckpt 2996: v0.89.23 shipped+release triggered; TT written (pause must stop all scannin
  6a8c02eed ckpt 2995: pre-release: v0.89.23: Check odds now also reads Novig's own price for ever
  715db5975 ckpt 2994: pre-ship: v0.89.23: Check odds now also reads Novig's own price for every o
  ac8a1c95e ckpt 2993: v0.89.22 released and recorded (TQ, TR)
  0d9e145f2 ckpt 2992: v0.89.22 shipped to main, release.yml triggered (TQ live lane, TR check odd
```

(13 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
