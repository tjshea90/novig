# CHECKPOINT 3008 — read me first, then TASKS.md

**Written:** 2026-10-11T20:56:10Z · **tests:** all 4 fast checks green
**Branch:** `ccr-4a7a6b39-3ig8vu` · **builds on:** `aa0959543` (this checkpoint is the commit after it)

## Just done
pre-release: v0.89.26: live lane says what is missing and keeps Novig's stream open, one API for overlapping data with failover, Kambi-desk books count once, auto-bet recovery status (versionCode 211, v0.89.26)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.89.26), then run: bash tools/record-release.sh v0.89.26 211 "v0.89.26: live lane says what is missing and keeps Novig's stream open, one API for overlapping data with failover, Kambi-desk books count once, auto-bet recovery status"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  14bbd467a ckpt 3007: TAB/TAC tested; wrote TAD (group books by shared odds provider)
  c5d07e192 ckpt 3006: TAB code (ApiChain) written, data tests green; TAC written (same-company bo
  4e46c73c7 ckpt 3005: TAA1 live-lane fixes in progress; wrote TAB (API priority chain: best API o
  8f1364ad1 ckpt 3004: TAA written (live lane banner bug, Novig books never arrive, auto-bet stopp
  d76ec702f ckpt 3003: v0.89.25 released and recorded (TS-TZZ done)
  8a76be00e ckpt 3002: pre-release: v0.89.25: no negative-EV order anywhere, a sharp book that dis
  9355f70e5 ckpt 3001: TX filters, TY auto-bet recovery done in code; wrote TZ (no negative-EV ord
  0ed57d4f5 ckpt 3000: TW done in code (reasons per bet, 2% EV cap for 3.5*+); wrote TX (live-only
  ddef5d3c3 ckpt 2999: TW written (autobet 4-star not betting; rating needs >=2% EV for 3.5+)
  bae6652c0 ckpt 2998: pre-release: v0.89.24: Pause stops everything at once, live lane keeps rows
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
