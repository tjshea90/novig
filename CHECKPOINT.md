# CHECKPOINT 2657 — read me first, then TASKS.md

**Written:** 2026-10-07T02:06:45Z · **tests:** all 4 fast checks green
**Branch:** `ccr-c79f7430-lq8xfl` · **builds on:** `b24e3a1e` (this checkpoint is the commit after it)

## Just done
DC2/DC3/DC4 data layer: shortest odds for the feed (minOdds), CNO (minOdds, SHORT_ODDS reason), bids (makerMinOdds) and the auto-bet generalized (positive = underdogs only) and now also checked on the order book just before the order; bid margin chips 2/2.5/3/3.25/3.5/4 (6 and 8 gone); Quick & likely now FILTERS obscure kinds and lines priced by fewer than makerQuickMinBooks (5) books; tests ShortestOddsTest, QuickBidFilterTest, CnoChecksTest, ApiBettingTest

## Do this next
UI: generic typed-number field, wire it to every numeric chip group, new shortest-odds rows, quick min books + margin typed; then reorganize Settings (DC1); mutation checks; full floor; ship v0.71.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  201ddd1b ckpt 2656: TASKS DC1-DC5 written in Tj's words (settings reorganize, typed numbers + sh
  a73f862c ckpt 2655: v0.70.1 analysis STOPPED at Tj's cost call after the 3 started verifiers fin
  eb7495c0 ckpt 2654: v0.70.4 RELEASED and RECORDED (code 122): ParlayAPI key rotation + free keys
  9c967742 ckpt 2653: pre-release: v0.70.4: ParlayAPI keys rotate: a scan goes to the next key whe
  ccdcdbcf ckpt 2652: v0.70.4 candidate: version bump 0.70.4/code 122, PARLAY_API.md section 4a (r
  54a3ff97 ckpt 2651: DB3 tests: client-level rotation to free keys (TheOddsApiClientTest), HISTOR
  5c08e9e4 ckpt 2650: DB1/DB2/DB4: free ParlayAPI keys now serve scans in Tj's key order after the
  b19f6a41 ckpt 2649: WRAP-UP (usage nearly out): 22/30 verifiers saved on GitHub plus 9/9 study, 
  6ee5134c ckpt 2648: 21/30 verifiers saved: rule 8 (R1: first look inside 6 h with EV>=2.5%) repr
  c391db77 ckpt 2647: 18/30 verifiers saved (rules 1-6 complete except rule 6 feasibility running;
```

(12 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
