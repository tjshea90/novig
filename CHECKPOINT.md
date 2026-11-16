# CHECKPOINT 4488 — read me first, then TASKS.md

**Written:** 2026-11-16T02:27:48Z · **tests:** all 10 fast checks green
**Branch:** `claude/optimistic-curie-gz2ti0` · **builds on:** `519ef05c0` (this checkpoint is the commit after it)

## Just done
pre-release: DG3: phone drops settled bets older than 35 days once GitHub holds their day (versionCode 454, v1.0.114)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.114), then run: bash tools/record-release.sh v1.0.114 454 "DG3: phone drops settled bets older than 35 days once GitHub holds their day"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  519ef05c0 ckpt 4487: pre-ship: DG3: phone drops settled bets older than 35 days once GitHub hold
  5664b7752 ckpt 4486: chat: DG3 logged (phone keeps only open+recent bets)
  c50e3c71e ckpt 4485: chat: v1.0.113 shipped (DG1a,c; OddsPapi off+cache gone); told Tj live/bids
  d725dde5d ckpt 4484: pre-release: DG1: bets.json saved at most every 4 s, OddsPapi Free cache de
  c9882254b ckpt 4483: pre-ship: DG1: bets.json saved at most every 4 s, OddsPapi Free cache delet
  d4216bb86 ckpt 4482: pre-ship: DG1: bets.json saved at most every 4 s, OddsPapi Free cache delet
  4b3f649d2 ckpt 4481: chat: DG1 diagnostics logged, answered
  a0ff17d31 ckpt 4480: WATCH+ANALYST cover 11-16: no change, engine healthy
  f66173dd6 ckpt 4479: v1.0.112 shipped; engine stop cleared, runner reset
  1b54b2b1e ckpt 4478: pre-release: Phone reads no Novig order books for bids while the engine pos
```
