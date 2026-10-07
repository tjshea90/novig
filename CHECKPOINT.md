# CHECKPOINT 2666 — read me first, then TASKS.md

**Written:** 2026-10-07T03:12:40Z · **tests:** all 4 fast checks green
**Branch:** `ccr-c79f7430-lq8xfl` · **builds on:** `11c55be5` (this checkpoint is the commit after it)

## Just done
pre-release: v0.71.1: the app faults the v0.70.1 analysis found are fixed: the scan-study export counts closes over started bets, keeps whole failure reasons and prints the void count; the Diagnostics file no longer hides older errors silently, shows when a scan really finished, counts folders in storage, counts 451s as refusals and says since when; the health lines no longer warn on normal quick fills, a half with nothing to match, or a provider the credit pacer already limits; the live feed retries after Novig's network refusal in 2 minutes like the key route; Vigilant's scan no longer buys ParlayAPI props for a league with no game in the window (RESEARCH.md section 101) (versionCode 124, v0.71.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.71.1), then run: bash tools/record-release.sh v0.71.1 124 "v0.71.1: the app faults the v0.70.1 analysis found are fixed: the scan-study export counts closes over started bets, keeps whole failure reasons and prints the void count; the Diagnostics file no longer hides older errors silently, shows when a scan really finished, counts folders in storage, counts 451s as refusals and says since when; the health lines no longer warn on normal quick fills, a half with nothing to match, or a provider the credit pacer already limits; the live feed retries after Novig's network refusal in 2 minutes like the key route; Vigilant's scan no longer buys ParlayAPI props for a league with no game in the window (RESEARCH.md section 101)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  11c55be5 ckpt 2665: TASKS DE0/DE1 written in Tj's words with the full plan for obscure-bid fill 
  9bc3df2f ckpt 2664: v0.71.1 candidate: DD1 app faults fixed (RESEARCH.md section 101), version 0
  42a856ef ckpt 2663: DD1 faults fixed with tests: study export, timeline cap, health lines, exit 
  fccffd7a ckpt 2662: TASKS DD1/DD2 written in Tj's words
  20fc2cab ckpt 2661: v0.71.0 RELEASED and RECORDED (code 123): settings pass DC1-DC5
  47402386 ckpt 2660: pre-release: v0.71.0: settings pass: Settings home grouped under four headin
  492f0a75 ckpt 2659: v0.71.0 candidate: settings pass (grouped home, ~45 typed boxes, shortest od
  55087eb7 ckpt 2658: DC1/DC2 UI: typed-number boxes (NumberSpec/TypedNumber.kt) on every numeric 
  74f13c05 ckpt 2657: DC2/DC3/DC4 data layer: shortest odds for the feed (minOdds), CNO (minOdds, 
  201ddd1b ckpt 2656: TASKS DC1-DC5 written in Tj's words (settings reorganize, typed numbers + sh
```
