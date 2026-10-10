# CHECKPOINT 2903 — read me first, then TASKS.md

**Written:** 2026-10-10T20:58:14Z · **tests:** all 4 fast checks green
**Branch:** `claude/new-session-mng5sq` · **builds on:** `2816b7e4` (this checkpoint is the commit after it)

## Just done
pre-release: v0.88.9: live bids and Autopilot use a 2% margin under Pinnacle's fair (was 3%): more fills (versionCode 182, v0.88.9)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.88.9), then run: bash tools/record-release.sh v0.88.9 182 "v0.88.9: live bids and Autopilot use a 2% margin under Pinnacle's fair (was 3%): more fills"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  2816b7e4 ckpt 2902: pre-ship: v0.88.9: live bids and Autopilot use a 2% margin under Pinnacle's 
  d47da744 ckpt 2901: Tj's 4:45 screenshots: live bids post for real on v0.88.8, 0 fills so far (T
  42c0e068 ckpt 2900: v0.88.8 released+recorded (live engine shares the one Novig stream)
  f73920e0 ckpt 2899: pre-release: v0.88.8: live engine shares the app's one Novig stream (a secon
  b16d7aa1 ckpt 2898: pre-ship: v0.88.8: live engine shares the app's one Novig stream (a second c
  56744e2b ckpt 2897: v0.88.7 released+recorded (live book refusal fix + reason words)
  61d6de37 ckpt 2896: pre-release: v0.88.7: live bids: a market Novig refuses no longer shuts the 
  9e16ef7f ckpt 2895: pre-ship: v0.88.7: live bids: a market Novig refuses no longer shuts the liv
  12ce33df ckpt 2894: pre-release: v0.88.6: pregame make-order bids leave a share of the wallet fo
  8db65eba ckpt 2893: v0.88.5 released+recorded; website feed ruled out for live (cached); waiting
```
