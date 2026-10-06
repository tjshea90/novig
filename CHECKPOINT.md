# CHECKPOINT 2632 — read me first, then TASKS.md

**Written:** 2026-10-06T19:37:10Z · **tests:** all 4 fast checks green
**Branch:** `ccr-b67c8afa-h14i94` · **builds on:** `f83ccfff` (this checkpoint is the commit after it)

## Just done
strategy-simple-filters saved (first look within 6 h of the start is the one simple filter that holds: CLV +1.41% on 256 closes, family-wise p 0.0005; EV>=2% inside it +3.01% on 79 closes; only +0.58% on independent closes); RESEARCH §98 apify written (not usable for live betting); diag-tracker-accuracy launched

## Do this next
running: strategy-timing-price, strategy-trap-avoid-and-props, diag-tracker-accuracy; then diag-bids-autobet, diag-lifecycle-errors; then verifiers (plan.py writes candidates.json once all 3 strategy files are saved), synthesis, critic; plan.py --running <labels>; save each with tools/save_agent.sh

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  600f47c7 ckpt 2631: restart after the session-limit stop: another session (9208ead4) had saved t
  3cb8fcda ckpt 2630: SAVED study-props-sharp-book, study-hidden-and-filters, study-traps, study-b
  82053167 ckpt 2629: SIXTH container (session 9208ead4): Tj re-sent both v0.70.1 files; extracted
  bc5b02cc ckpt 2628: SAVE-NOW: v0.70.2 released+recorded; 5 of 14 phase-1 analysts saved on GitHu
  50d60335 ckpt 2627: 4 of 9 study analysts saved on GitHub (overall-edge, splits-bet-attributes, 
  8f3e12e6 ckpt 2626: v0.70.2 RELEASED + recorded (Release link in TASKS CX3); study-overall-edge 
  ac396409 ckpt 2625: files re-sent and extracted (loader matches the phone); wave 1 launched, stu
  90a3bcf0 ckpt 2624: pre-release: v0.70.2: the Diagnostics file always says whether the live burs
  e6ba9e93 ckpt 2623: built plan.py (wave planner, selftest) + save_agent.sh (bank one agent's res
  2b3b86ab ckpt 2622: Tj's 14:14Z instruction recorded as CY1-CY3 in TASKS.md (max 3 agents in fli
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
