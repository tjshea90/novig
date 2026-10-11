# CHECKPOINT 2995 — read me first, then TASKS.md

**Written:** 2026-10-11T19:25:09Z · **tests:** all 4 fast checks green
**Branch:** `ccr-4a7a6b39-3ig8vu` · **builds on:** `715db5975` (this checkpoint is the commit after it)

## Just done
pre-release: v0.89.23: Check odds now also reads Novig's own price for every open bet it missed (versionCode 208, v0.89.23)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.89.23), then run: bash tools/record-release.sh v0.89.23 208 "v0.89.23: Check odds now also reads Novig's own price for every open bet it missed"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  715db5975 ckpt 2994: pre-ship: v0.89.23: Check odds now also reads Novig's own price for every o
  ac8a1c95e ckpt 2993: v0.89.22 released and recorded (TQ, TR)
  0d9e145f2 ckpt 2992: v0.89.22 shipped to main, release.yml triggered (TQ live lane, TR check odd
  327196d38 ckpt 2991: pre-release: v0.89.22: real-time live lane (Pinnodds+Novig, 5 s rule), Chec
  2789b3561 ckpt 2990: pre-ship: v0.89.22: real-time live lane (Pinnodds+Novig, 5 s rule), Check o
  e8dc299d2 ckpt 2989: pre-ship: v0.89.22: real-time live lane (Pinnodds+Novig, 5 s rule), Check o
  0b4f6e441 ckpt 2988: TQ done in code+tests (live lane, manual live bets re-read fair); wrote TR 
  457a2e91f ckpt 2987: TQ: live lane built (LiveScan.kt, runner scanPass, VM merge, Feed banner, l
  3b69fdebd ckpt 2986: v0.89.20 (live bets by hand) and v0.89.21 (star rating decides auto-bets) r
  5a28e0ffa ckpt 2985: pre-release: v0.89.21: a minimum star rating in Auto-bet now decides which 
```
