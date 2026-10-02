# CHECKPOINT 2317 — read me first, then TASKS.md

**Written:** 2026-10-02T06:03:34Z · **tests:** all 3 fast checks green
**Branch:** `ccr-ea5bf769-s5xd3t` · **builds on:** `8debb4eb` (this checkpoint is the commit after it)

## Just done
AQ1/AQ3/AQ4/AR1/AR2 ticked; app-level manual-bet test added (ApiBettingControllerTest), its mutant killed

## Do this next
AQ2: the lag during a Vigilant scan (measure: ScanService notification feed, mini window, auto-scan concurrency; add a jank meter to Diagnostics)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  11e9ba2b ckpt 2316: AR1/AR2: Check odds now and Price now price Vigilant's bets whatever the sca
  9c48a379 ckpt 2315: AR written to TASKS.md (Check odds now must refresh every open bet, Vigilant
  fb432429 ckpt 2314: AQ3/AQ4: manual Bet sheet has no min EV / fair-age / pause block (BetLimits.
  88d819f2 ckpt 2313: v0.44.1 released and recorded (AP1): refusal under the minimum names the min
  eb5c7763 ckpt 2312: pre-release: v0.44.1: a bet under your minimum edge says so and where to cha
  412fd980 ckpt 2311: AP1: a positive edge under Tj's minimum now says so and names the setting (w
  6a52c87b ckpt 2310: v0.44.0 released and recorded (AO1-AO6 done): lag fix, ParlayAPI freshness, 
  07ce6e1a ckpt 2309: pre-release: v0.44.0: smoother +EV list during scans, ParlayAPI quotes dated
  d8695cbe ckpt 2308: AO1 full tests done: floor 1604/23/0 with screenshots, 97 PNGs looked at, 3 
  df8ee1b3 ckpt 2307: RESEARCH §63, PARLAY_API/NOVIG_API notes, TASKS AO2-AO5 ticked, KeyActions 
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
