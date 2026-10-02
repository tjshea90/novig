# CHECKPOINT 2314 — read me first, then TASKS.md

**Written:** 2026-10-02T05:55:36Z · **tests:** all 3 fast checks green
**Branch:** `ccr-ea5bf769-s5xd3t` · **builds on:** `fb25957a` (this checkpoint is the commit after it)

## Just done
AQ3/AQ4: manual Bet sheet has no min EV / fair-age / pause block (BetLimits.manual), 423 codes named and bet-level locks skip one bet; tests pass

## Do this next
mutation-check new ApiBettingTest cases; new ask AR (Check odds now refreshes Vigilant bets too); AQ2 lag; bump 0.44.2, floor, ship, answer Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  88d819f2 ckpt 2313: v0.44.1 released and recorded (AP1): refusal under the minimum names the min
  eb5c7763 ckpt 2312: pre-release: v0.44.1: a bet under your minimum edge says so and where to cha
  412fd980 ckpt 2311: AP1: a positive edge under Tj's minimum now says so and names the setting (w
  6a52c87b ckpt 2310: v0.44.0 released and recorded (AO1-AO6 done): lag fix, ParlayAPI freshness, 
  07ce6e1a ckpt 2309: pre-release: v0.44.0: smoother +EV list during scans, ParlayAPI quotes dated
  d8695cbe ckpt 2308: AO1 full tests done: floor 1604/23/0 with screenshots, 97 PNGs looked at, 3 
  df8ee1b3 ckpt 2307: RESEARCH §63, PARLAY_API/NOVIG_API notes, TASKS AO2-AO5 ticked, KeyActions 
  2128b83f ckpt 2306: Diagnostics review fixes: onTrimMemory drops boards past the freshness limit
  c9ed0bd8 ckpt 2305: AO2 lag found and fixed: root built a new ApiBetActions for the STATIC Local
  354f4eb9 ckpt 2304: Live feed: opened at the first plan, ONE bulk subscribe of the unread lines 
```

(10 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
