# CHECKPOINT 2635 — read me first, then TASKS.md

**Written:** 2026-10-06T20:25:52Z · **tests:** all 4 fast checks green
**Branch:** `ccr-28ef9ece-n2y0gt` · **builds on:** `19af2cc6` (this checkpoint is the commit after it)

## Just done
RESUMED (7th container, branch ccr-28ef9ece-n2y0gt): hooks installed; two v0.70.1 files re-extracted into scratchpad v0701/; 3 lost diag analysts relaunched (diag-tracker-accuracy, diag-bids-autobet, diag-lifecycle-errors); new requests written as TASKS DA1-DA4; built tools/research/live_feed_race.py (multi-feed score race vs Novig trades) and started an 8 h overnight recording (scratchpad race/night1.ndjson, ends ~04:30Z) for DA2; found: Polymarket sports websocket is free+keyless and reachable, MLB gameday push socket reachable, ESPN FastCast bootstrap is /public/websockethost but its port is blocked here, CDN caches (ESPN 6s, MLB 20s, NHL 19s, Sofa 5s)

## Do this next
1) when diag agents finish: bash tools/save_agent.sh <label>; then launch verify-1..10 x reproduce|luck|feasibility THREE at a time (plan.py --running); 2) write RESEARCH.md §99 (live feed) from the race output; 3) DA3 low-usage margin 1.5%/typed and DA4 trap guard typed hours (12 h chip already exists), tests+ship

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
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

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
