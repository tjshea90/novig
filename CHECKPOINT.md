# CHECKPOINT 2607 — read me first, then TASKS.md

**Written:** 2026-10-06T02:50:04Z · **tests:** all 3 fast checks green
**Branch:** `ccr-3436e911-cyln4u` · **builds on:** `03852e89` (this checkpoint is the commit after it)

## Just done
pre-release: v0.69.0: live burst recorder (no orders): watches live games' lines on the read key, finds cross-line mispricings after plays, paper-trades them at your measured delays, verdict + share file (versionCode 117, v0.69.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.69.0), then run: bash tools/record-release.sh v0.69.0 117 "v0.69.0: live burst recorder (no orders): watches live games' lines on the read key, finds cross-line mispricings after plays, paper-trades them at your measured delays, verdict + share file"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  5a183237 ckpt 2606: v0.69.0 burst recorder: full floor green (2,282), TASKS/NOVIG_API updated, v
  46753e50 ckpt 2605: score-burst recorder built: ladders, covers, windows, paper trader, delays, 
  49db12ad ckpt 2604: Tj asks for a no-orders score-burst recorder in the app and whether it works
  9bf6ef80 ckpt 2603: answered Tj: 'Any time' doesn't widen Low API usage (mode's own 6 h rule); t
  75d489bf ckpt 2602: v0.68.1 RELEASED and recorded (run 122); main merged with one inbox-only com
  444974be ckpt 2601: pre-release: v0.68.1: Low API usage bids stay up (Auto pace: a scan starts b
  10ad5b04 ckpt 2600: v0.68.1 ready: Auto pace + resting markets first + the +EV tab/timeline/heal
  9ee7b975 ckpt 2599: CR5 code + tests green (Auto pace, resting markets pinned, mutants killed); 
  edf8a14c ckpt 2598: CR1/CR2/CR5 code written (Auto pace + resting markets read first, 9 timeline
  e398422c ckpt 2597: CR4/CR5 written into TASKS.md (Tj: bids all cancel together; asks for a fres
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
