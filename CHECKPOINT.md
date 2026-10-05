# CHECKPOINT 2530 — read me first, then TASKS.md

**Written:** 2026-10-05T02:28:30Z · **tests:** all 3 fast checks green
**Branch:** `ccr-44a73259-01ykiu` · **builds on:** `e7d1a1d5` (this checkpoint is the commit after it)

## Just done
pre-release: v0.61.0: Starts within gets 3h and 6h (the lists can match the 6 h trap guard where Tj's bets keep their edge), Tracker Where it's working gets a Time to start chip, Diagnostics judges edge accuracy on bets placed inside the guard's window and flags early bets, a sleeping scanner's old bets are a warning not a failure, the scan study's summary carries a ± that counts games (versionCode 108, v0.61.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.61.0), then run: bash tools/record-release.sh v0.61.0 108 "v0.61.0: Starts within gets 3h and 6h (the lists can match the 6 h trap guard where Tj's bets keep their edge), Tracker Where it's working gets a Time to start chip, Diagnostics judges edge accuracy on bets placed inside the guard's window and flags early bets, a sleeping scanner's old bets are a warning not a failure, the scan study's summary carries a ± that counts games"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  e7d1a1d5 ckpt 2529: CA1-CA4 built and tested (Starts within 3h/6h, Time to start chip, health ch
  102eacfc ckpt 2528: wrote Tj's 2026-10-05 request (CLV/EV review of v0.60.0 files) into TASKS.md
  bb75cbac ckpt 2527: v0.60.0 released + recorded (release.yml run 37236337851 green, CI 372356322
  e1d61b78 ckpt 2526: pre-release: v0.60.0: a closed market no longer sends the Novig scan to the 
```
