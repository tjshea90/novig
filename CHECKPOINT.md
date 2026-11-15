# CHECKPOINT 4467 — read me first, then TASKS.md

**Written:** 2026-11-15T20:05:43Z · **tests:** all 10 fast checks green
**Branch:** `claude/optimistic-curie-f65yc8` · **builds on:** `598aefc00` (this checkpoint is the commit after it)

## Just done
pre-release: LG3b: with the GitHub engine driving, the phone no longer logs, grades or closes the scan study itself; the Feed's ticks and rechecks slow down on low-RAM devices (versionCode 449, v1.0.109)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.109), then run: bash tools/record-release.sh v1.0.109 449 "LG3b: with the GitHub engine driving, the phone no longer logs, grades or closes the scan study itself; the Feed's ticks and rechecks slow down on low-RAM devices"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  598aefc00 ckpt 4466: pre-ship: LG3b: with the GitHub engine driving, the phone no longer logs, g
  9661216ae ckpt 4465: pre-release: LG3: low-RAM devices (Android Go tablets) read the engine 2.5x
  8051a60b7 ckpt 4464: pre-ship: LG3: low-RAM devices (Android Go tablets) read the engine 2.5x le
  277a149c3 ckpt 4463: pre-ship: LG3: low-RAM devices (Android Go tablets) read the engine 2.5x le
  260a3ddd9 ckpt 4462: ENGINEER 11-15 slot 1 re-check: no change (wallet $0.01)
  1d327bc4b ckpt 4461: ENGINEER 11-15 slot: watch only, no change
  3bfbf76ee ckpt 4460: chat: NC4 shipped v1.0.107; tax Qs answered; LG2 findings logged
  367f36f0e ckpt 4459: pre-release: NC4: NCAAB Bet buttons matched to the right team by the side's
  44aec1bdc ckpt 4458: NC4: NCAAB sides matched by team name
  44265fa0d ckpt 4457: CN1 resolved (phone writing again); PropLine log reworded; LG2 findings
```
