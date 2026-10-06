# CHECKPOINT 2636 — read me first, then TASKS.md

**Written:** 2026-10-06T20:41:12Z · **tests:** all 4 fast checks green
**Branch:** `ccr-28ef9ece-n2y0gt` · **builds on:** `bebad07b` (this checkpoint is the commit after it)

## Just done
pre-release: v0.70.3: Low API usage bids' margin under the fair has a 1.5% chip and a field for any percent from 0.5 to 50 (2.5% stays the default); the trap guard's window (Auto-bet, alerts, Bids) has a field for any whole number of hours beside the 3/6/12/24 h chips (versionCode 121, v0.70.3)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.70.3), then run: bash tools/record-release.sh v0.70.3 121 "v0.70.3: Low API usage bids' margin under the fair has a 1.5% chip and a field for any percent from 0.5 to 50 (2.5% stays the default); the trap guard's window (Auto-bet, alerts, Bids) has a field for any whole number of hours beside the 3/6/12/24 h chips"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  eeffb02a ckpt 2635: RESUMED (7th container, branch ccr-28ef9ece-n2y0gt): hooks installed; two v0
  5ac0a346 ckpt 2634: WRAP-UP: all 9 study + 3 strategy + 2 of 5 diag analysts and candidates.json
  9c9950c2 ckpt 2633: phase 2 complete: all 3 strategy builders saved, plan.py wrote candidates.js
  0c2ebb34 ckpt 2632: strategy-simple-filters saved (first look within 6 h of the start is the one
  600f47c7 ckpt 2631: restart after the session-limit stop: another session (9208ead4) had saved t
  3cb8fcda ckpt 2630: SAVED study-props-sharp-book, study-hidden-and-filters, study-traps, study-b
  82053167 ckpt 2629: SIXTH container (session 9208ead4): Tj re-sent both v0.70.1 files; extracted
  bc5b02cc ckpt 2628: SAVE-NOW: v0.70.2 released+recorded; 5 of 14 phase-1 analysts saved on GitHu
  50d60335 ckpt 2627: 4 of 9 study analysts saved on GitHub (overall-edge, splits-bet-attributes, 
  8f3e12e6 ckpt 2626: v0.70.2 RELEASED + recorded (Release link in TASKS CX3); study-overall-edge 
```

(21 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
