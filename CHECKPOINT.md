# CHECKPOINT 2659 — read me first, then TASKS.md

**Written:** 2026-10-07T02:31:13Z · **tests:** all 4 fast checks green
**Branch:** `ccr-c79f7430-lq8xfl` · **builds on:** `cac9da07` (this checkpoint is the commit after it)

## Just done
v0.71.0 candidate: settings pass (grouped home, ~45 typed boxes, shortest odds in feed/CNO/auto-bet/bids, merged bet amount, quick bids filter strange/small markets, margins 2-4%), RESEARCH section 100, TASKS DC1-DC5 ticked; floor 2372 tests green

## Do this next
wait for CI green on this commit, then bash ship.sh, trigger release.yml, record-release, send Tj the link and the short answer incl. the fast-feed status

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
    M  TASKS.md
    M  app/build.gradle.kts

## Last ten checkpoints
```
  55087eb7 ckpt 2658: DC1/DC2 UI: typed-number boxes (NumberSpec/TypedNumber.kt) on every numeric 
  74f13c05 ckpt 2657: DC2/DC3/DC4 data layer: shortest odds for the feed (minOdds), CNO (minOdds, 
  201ddd1b ckpt 2656: TASKS DC1-DC5 written in Tj's words (settings reorganize, typed numbers + sh
  a73f862c ckpt 2655: v0.70.1 analysis STOPPED at Tj's cost call after the 3 started verifiers fin
  eb7495c0 ckpt 2654: v0.70.4 RELEASED and RECORDED (code 122): ParlayAPI key rotation + free keys
  9c967742 ckpt 2653: pre-release: v0.70.4: ParlayAPI keys rotate: a scan goes to the next key whe
  ccdcdbcf ckpt 2652: v0.70.4 candidate: version bump 0.70.4/code 122, PARLAY_API.md section 4a (r
  54a3ff97 ckpt 2651: DB3 tests: client-level rotation to free keys (TheOddsApiClientTest), HISTOR
  5c08e9e4 ckpt 2650: DB1/DB2/DB4: free ParlayAPI keys now serve scans in Tj's key order after the
  b19f6a41 ckpt 2649: WRAP-UP (usage nearly out): 22/30 verifiers saved on GitHub plus 9/9 study, 
```

(9 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
