# CHECKPOINT 2637 — read me first, then TASKS.md

**Written:** 2026-10-06T20:45:42Z · **tests:** all 4 fast checks green
**Branch:** `ccr-28ef9ece-n2y0gt` · **builds on:** `1df7b375` (this checkpoint is the commit after it)

## Just done
v0.70.3 SHIPPED (pushed; code 121: Low API usage margin chip 1.5% + typed percent; trap guard typed hours; 12 h chip already existed); CI running on quiet branch ci-v0.70.3 (main is cancelled by autosave pushes); RELEASE NOT YET TRIGGERED: after ci-v0.70.3 is green trigger release.yml with ref=ci-v0.70.3 (NOT main: main now has unreleased DA7 code), confirm get_release_by_tag v0.70.3, then bash tools/record-release.sh v0.70.3 121 '<note>' and send Tj the link. DA7 (ReplyShape: unreadable batch reply now says its shape) coded+tested on main, unreleased. Analysis: 14/14 phase-1 saved; verify-1 x3 saved (rule 1 'Late sniper' does NOT survive: Tracker-close circularity); verify-2-reproduce/luck/feasibility running. Feed research: RESEARCH §99 draft in repo (measure section 99.7 pending the overnight race recorder: scratchpad race/night1.ndjson ends ~04:30Z); Scrapeless added (99.4b)

## Do this next
1) ci-v0.70.3 green -> release v0.70.3 from that ref; 2) keep 3 verifiers in flight (plan.py --running), save each; 3) after the games: python3 tools/research/live_feed_race.py analyze <tape> and fill RESEARCH §99.7; 4) synthesis+critic then RESEARCH §97 + research/scan_study_analysis_2026-10-06_v0.70.1.md + short-bullet answer

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  d168c5ac ckpt 2636: pre-release: v0.70.3: Low API usage bids' margin under the fair has a 1.5% c
  eeffb02a ckpt 2635: RESUMED (7th container, branch ccr-28ef9ece-n2y0gt): hooks installed; two v0
  5ac0a346 ckpt 2634: WRAP-UP: all 9 study + 3 strategy + 2 of 5 diag analysts and candidates.json
  9c9950c2 ckpt 2633: phase 2 complete: all 3 strategy builders saved, plan.py wrote candidates.js
  0c2ebb34 ckpt 2632: strategy-simple-filters saved (first look within 6 h of the start is the one
  600f47c7 ckpt 2631: restart after the session-limit stop: another session (9208ead4) had saved t
  3cb8fcda ckpt 2630: SAVED study-props-sharp-book, study-hidden-and-filters, study-traps, study-b
  82053167 ckpt 2629: SIXTH container (session 9208ead4): Tj re-sent both v0.70.1 files; extracted
  bc5b02cc ckpt 2628: SAVE-NOW: v0.70.2 released+recorded; 5 of 14 phase-1 analysts saved on GitHu
  50d60335 ckpt 2627: 4 of 9 study analysts saved on GitHub (overall-edge, splits-bet-attributes, 
```

(9 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
