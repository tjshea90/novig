# CHECKPOINT 2658 — read me first, then TASKS.md

**Written:** 2026-10-07T02:18:45Z · **tests:** all 4 fast checks green
**Branch:** `ccr-c79f7430-lq8xfl` · **builds on:** `cc4c1536` (this checkpoint is the commit after it)

## Just done
DC1/DC2 UI: typed-number boxes (NumberSpec/TypedNumber.kt) on every numeric chip group of Settings, Auto-bet, Bids, betting limits, burst trader and Pinnacle only; new shortest-odds rows for the feed, CNO list and bids; odds range warnings; quick-bid market size setting; Settings home grouped under four headings (Betting & alerts / Finding bets / On screen / Data & help); tests TypedNumberSpecTest, TypedNumbersUiTest, AutoBetUiTest, MakerUiTest

## Do this next
mutation checks on the new tests, full floor, then README/docs (BRIEF, CHANGELOG notes), the report of what moved, ship v0.71.0, answer DC5 (fast-feed status)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
    M  app/src/test/kotlin/com/tjshea/vigilant/app/MakerUiTest.kt

## Last ten checkpoints
```
  74f13c05 ckpt 2657: DC2/DC3/DC4 data layer: shortest odds for the feed (minOdds), CNO (minOdds, 
  201ddd1b ckpt 2656: TASKS DC1-DC5 written in Tj's words (settings reorganize, typed numbers + sh
  a73f862c ckpt 2655: v0.70.1 analysis STOPPED at Tj's cost call after the 3 started verifiers fin
  eb7495c0 ckpt 2654: v0.70.4 RELEASED and RECORDED (code 122): ParlayAPI key rotation + free keys
  9c967742 ckpt 2653: pre-release: v0.70.4: ParlayAPI keys rotate: a scan goes to the next key whe
  ccdcdbcf ckpt 2652: v0.70.4 candidate: version bump 0.70.4/code 122, PARLAY_API.md section 4a (r
  54a3ff97 ckpt 2651: DB3 tests: client-level rotation to free keys (TheOddsApiClientTest), HISTOR
  5c08e9e4 ckpt 2650: DB1/DB2/DB4: free ParlayAPI keys now serve scans in Tj's key order after the
  b19f6a41 ckpt 2649: WRAP-UP (usage nearly out): 22/30 verifiers saved on GitHub plus 9/9 study, 
  6ee5134c ckpt 2648: 21/30 verifiers saved: rule 8 (R1: first look inside 6 h with EV>=2.5%) repr
```

(15 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
