# CHECKPOINT 2991 — read me first, then TASKS.md

**Written:** 2026-10-11T18:58:35Z · **tests:** all 4 fast checks green
**Branch:** `ccr-4a7a6b39-3ig8vu` · **builds on:** `2789b3561` (this checkpoint is the commit after it)

## Just done
pre-release: v0.89.22: real-time live lane (Pinnodds+Novig, 5 s rule), Check odds counts in-play bets, Hide live games (versionCode 207, v0.89.22)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.89.22), then run: bash tools/record-release.sh v0.89.22 207 "v0.89.22: real-time live lane (Pinnodds+Novig, 5 s rule), Check odds counts in-play bets, Hide live games"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  2789b3561 ckpt 2990: pre-ship: v0.89.22: real-time live lane (Pinnodds+Novig, 5 s rule), Check o
  e8dc299d2 ckpt 2989: pre-ship: v0.89.22: real-time live lane (Pinnodds+Novig, 5 s rule), Check o
  0b4f6e441 ckpt 2988: TQ done in code+tests (live lane, manual live bets re-read fair); wrote TR 
  457a2e91f ckpt 2987: TQ: live lane built (LiveScan.kt, runner scanPass, VM merge, Feed banner, l
  3b69fdebd ckpt 2986: v0.89.20 (live bets by hand) and v0.89.21 (star rating decides auto-bets) r
  5a28e0ffa ckpt 2985: pre-release: v0.89.21: a minimum star rating in Auto-bet now decides which 
  e7cbe3118 ckpt 2984: pre-ship: v0.89.21: a minimum star rating in Auto-bet now decides which bet
  b91c3a22d ckpt 2983: TP done: star rating overrides the selection filters (3 lanes), UI note, te
  f69319c26 ckpt 2982: TP: wrote the star-rating-overrides-filters request into TASKS.md; v0.89.20
  96de1ae98 ckpt 2981: pre-release: v0.89.20: bets by hand on live games (the Bet sheet no longer 
```
