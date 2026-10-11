# CHECKPOINT 2987 — read me first, then TASKS.md

**Written:** 2026-10-11T18:37:26Z · **tests:** all 4 fast checks green
**Branch:** `ccr-4a7a6b39-3ig8vu` · **builds on:** `f67c0f52c` (this checkpoint is the commit after it)

## Just done
TQ: live lane built (LiveScan.kt, runner scanPass, VM merge, Feed banner, live-fair manual bet); compiles

## Do this next
run full tests, fix feed() fallout, write LiveScanTest (freshness 5s drop, switch off unchanged), docs, ship

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  3b69fdebd ckpt 2986: v0.89.20 (live bets by hand) and v0.89.21 (star rating decides auto-bets) r
  5a28e0ffa ckpt 2985: pre-release: v0.89.21: a minimum star rating in Auto-bet now decides which 
  e7cbe3118 ckpt 2984: pre-ship: v0.89.21: a minimum star rating in Auto-bet now decides which bet
  b91c3a22d ckpt 2983: TP done: star rating overrides the selection filters (3 lanes), UI note, te
  f69319c26 ckpt 2982: TP: wrote the star-rating-overrides-filters request into TASKS.md; v0.89.20
  96de1ae98 ckpt 2981: pre-release: v0.89.20: bets by hand on live games (the Bet sheet no longer 
  30bddb6b4 ckpt 2980: pre-ship: v0.89.20: bets by hand on live games (the Bet sheet no longer ref
  bb685c195 ckpt 2979: TO2 done with tests; docs (NOVIG_API §24, RESEARCH 132.3, CLAUDE.md); TO3/
  bc1e90049 ckpt 2978: TO2: manual live bets (planner prices live fee, live note, betRefused codes
  d4d5a3f69 ckpt 2977: TO: wrote Tj's live-betting request into TASKS.md; answer = Vigilant rule n
```

(13 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
