# CHECKPOINT 3916 — read me first, then TASKS.md

**Written:** 2026-10-21T19:15:51Z · **tests:** all 9 fast checks green
**Branch:** `claude/optimistic-curie-s9zcjn` · **builds on:** `fce8dea44` (this checkpoint is the commit after it)

## Just done
pre-release: v1.0.38: API card trusts a fresh accepted key check over a stale refusal (BoltOdds card) (versionCode 378, v1.0.38)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.38), then run: bash tools/record-release.sh v1.0.38 378 "v1.0.38: API card trusts a fresh accepted key check over a stale refusal (BoltOdds card)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  e96c94d08 ckpt 3915: ApiState: a fresh accepted key check outranks a ledger 'every key refused' 
  4c0826410 ckpt 3914: pre-release: v1.0.37: sharp-confirmed bids step their margin down to 1.5% u
  cb7b3025c ckpt 3913: pre-ship: v1.0.37: sharp-confirmed bids step their margin down to 1.5% unde
  edf8cc5e2 ckpt 3913: ANALYST 2026-10-21 skipped: under 100 new settled; engine healthy
  70c07be9a ckpt 3912: CH59 sharp bid margin step-down merged onto main line, tests green (2863), 
  f9837152a ckpt 3901: CH59 sharp-confirmed bid margin step-down to 1.5% (tests green, not release
  590ef9bb4 ckpt 3911: Research index landed
  13541563e ckpt 3910: Research library: tools/research_index.py + research/INDEX.md (104 studies,
  20f4af5d8 ckpt 3909: pre-release: v1.0.36: feed-level API problems on the API card, order gradin
  a0eaf1a28 ckpt 3908: Merged cut-off branch ccr-cb0c319e (feed-level API problems, order grading 
```
