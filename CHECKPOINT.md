# CHECKPOINT 3909 — read me first, then TASKS.md

**Written:** 2026-10-21T16:58:36Z · **tests:** all 8 fast checks green
**Branch:** `claude/new-session-51eqix` · **builds on:** `a0eaf1a28` (this checkpoint is the commit after it)

## Just done
pre-release: v1.0.36: feed-level API problems on the API card, order grading fix (orders with no logged line), FCS games in NCAAF grading, volume report tool (versionCode 376, v1.0.36)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.36), then run: bash tools/record-release.sh v1.0.36 376 "v1.0.36: feed-level API problems on the API card, order grading fix (orders with no logged line), FCS games in NCAAF grading, volume report tool"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  a0eaf1a28 ckpt 3908: Merged cut-off branch ccr-cb0c319e (feed-level API problems, order grading 
  181fe07e0 ckpt 3907: XO1 feed-level problems (API manager note+message, card), XO2 orders withou
  291b94c40 ckpt 3906: XO3/XO4 studies written, write-behind desk store tested (4112 tests green),
  9d65a42cc ckpt 3905: XO2 plumbing: JsonFileStore write-behind for the engine desk (+keepEndedMs)
  7d42db006 ckpt 3904: pre-release: v1.0.35: API manager (restart, fresh runner, switch off + mess
  3ef63bc41 ckpt 3903: pre-ship: v1.0.35: API manager (restart, fresh runner, switch off + message
  2ee439375 ckpt 3902: XO1 complete (ApiState evidence rules, phone checks on pull-to-refresh, API
  014eff966 ckpt 3901: XO1 engine: ParlayAPI check was N x N reads (root cause of false 'no answer
```
