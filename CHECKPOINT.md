# CHECKPOINT 3012 — read me first, then TASKS.md

**Written:** 2026-10-11T21:23:45Z · **tests:** all 4 fast checks green
**Branch:** `ccr-4a7a6b39-3ig8vu` · **builds on:** `2cb09a107` (this checkpoint is the commit after it)

## Just done
pre-release: v0.89.27: live Novig books subscribe at once and the feed reconnects in seconds after a drop (versionCode 212, v0.89.27)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.89.27), then run: bash tools/record-release.sh v0.89.27 212 "v0.89.27: live Novig books subscribe at once and the feed reconnects in seconds after a drop"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  2cb09a107 ckpt 3011: pre-ship: v0.89.27: live Novig books subscribe at once and the feed reconne
  5b46ec775 ckpt 3010: TAE written (live books 'not subscribed yet')
  83fc14973 ckpt 3009: v0.89.26 released and recorded (TAA, TAB, TAC, TAD done)
  979441023 ckpt 3008: pre-release: v0.89.26: live lane says what is missing and keeps Novig's str
  14bbd467a ckpt 3007: TAB/TAC tested; wrote TAD (group books by shared odds provider)
  c5d07e192 ckpt 3006: TAB code (ApiChain) written, data tests green; TAC written (same-company bo
  4e46c73c7 ckpt 3005: TAA1 live-lane fixes in progress; wrote TAB (API priority chain: best API o
  8f1364ad1 ckpt 3004: TAA written (live lane banner bug, Novig books never arrive, auto-bet stopp
  d76ec702f ckpt 3003: v0.89.25 released and recorded (TS-TZZ done)
  8a76be00e ckpt 3002: pre-release: v0.89.25: no negative-EV order anywhere, a sharp book that dis
```
