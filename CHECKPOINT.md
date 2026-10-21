# CHECKPOINT 3914 — read me first, then TASKS.md

**Written:** 2026-10-21T17:30:43Z · **tests:** all 9 fast checks green
**Branch:** `claude/optimistic-curie-spy7mj` · **builds on:** `4d8dc0b63` (this checkpoint is the commit after it)

## Just done
pre-release: v1.0.37: sharp-confirmed bids step their margin down to 1.5% under the sharp book as the game nears (CH59) (versionCode 377, v1.0.37)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.37), then run: bash tools/record-release.sh v1.0.37 377 "v1.0.37: sharp-confirmed bids step their margin down to 1.5% under the sharp book as the game nears (CH59)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  cb7b3025c ckpt 3913: pre-ship: v1.0.37: sharp-confirmed bids step their margin down to 1.5% unde
  edf8cc5e2 ckpt 3913: ANALYST 2026-10-21 skipped: under 100 new settled; engine healthy
  70c07be9a ckpt 3912: CH59 sharp bid margin step-down merged onto main line, tests green (2863), 
  f9837152a ckpt 3901: CH59 sharp-confirmed bid margin step-down to 1.5% (tests green, not release
  590ef9bb4 ckpt 3911: Research index landed
  13541563e ckpt 3910: Research library: tools/research_index.py + research/INDEX.md (104 studies,
  20f4af5d8 ckpt 3909: pre-release: v1.0.36: feed-level API problems on the API card, order gradin
  a0eaf1a28 ckpt 3908: Merged cut-off branch ccr-cb0c319e (feed-level API problems, order grading 
  181fe07e0 ckpt 3907: XO1 feed-level problems (API manager note+message, card), XO2 orders withou
  291b94c40 ckpt 3906: XO3/XO4 studies written, write-behind desk store tested (4112 tests green),
```
