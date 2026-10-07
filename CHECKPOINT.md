# CHECKPOINT 2660 — read me first, then TASKS.md

**Written:** 2026-10-07T02:39:28Z · **tests:** all 4 fast checks green
**Branch:** `ccr-c79f7430-lq8xfl` · **builds on:** `492f0a75` (this checkpoint is the commit after it)

## Just done
pre-release: v0.71.0: settings pass: Settings home grouped under four headings and searchable down to every setting; a typed box beside every setting with number options (about 45); shortest odds beside every longest odds (feed, CrazyNinjaOdds list, auto-bet, bids), the auto-bet's also checked on the order book just before the order; the bet sheet amount merged into My amount; bid margins 2/2.5/3/3.25/3.5/4% (6 and 8 removed); Quick & likely bids now skip strange props and small markets (a books-priced floor you can set) (versionCode 123, v0.71.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.71.0), then run: bash tools/record-release.sh v0.71.0 123 "v0.71.0: settings pass: Settings home grouped under four headings and searchable down to every setting; a typed box beside every setting with number options (about 45); shortest odds beside every longest odds (feed, CrazyNinjaOdds list, auto-bet, bids), the auto-bet's also checked on the order book just before the order; the bet sheet amount merged into My amount; bid margins 2/2.5/3/3.25/3.5/4% (6 and 8 removed); Quick & likely bids now skip strange props and small markets (a books-priced floor you can set)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  492f0a75 ckpt 2659: v0.71.0 candidate: settings pass (grouped home, ~45 typed boxes, shortest od
  55087eb7 ckpt 2658: DC1/DC2 UI: typed-number boxes (NumberSpec/TypedNumber.kt) on every numeric 
  74f13c05 ckpt 2657: DC2/DC3/DC4 data layer: shortest odds for the feed (minOdds), CNO (minOdds, 
  201ddd1b ckpt 2656: TASKS DC1-DC5 written in Tj's words (settings reorganize, typed numbers + sh
  a73f862c ckpt 2655: v0.70.1 analysis STOPPED at Tj's cost call after the 3 started verifiers fin
  eb7495c0 ckpt 2654: v0.70.4 RELEASED and RECORDED (code 122): ParlayAPI key rotation + free keys
  9c967742 ckpt 2653: pre-release: v0.70.4: ParlayAPI keys rotate: a scan goes to the next key whe
  ccdcdbcf ckpt 2652: v0.70.4 candidate: version bump 0.70.4/code 122, PARLAY_API.md section 4a (r
  54a3ff97 ckpt 2651: DB3 tests: client-level rotation to free keys (TheOddsApiClientTest), HISTOR
  5c08e9e4 ckpt 2650: DB1/DB2/DB4: free ParlayAPI keys now serve scans in Tj's key order after the
```
