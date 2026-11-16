# CHECKPOINT 4484 — read me first, then TASKS.md

**Written:** 2026-11-16T02:03:52Z · **tests:** all 10 fast checks green
**Branch:** `claude/optimistic-curie-gz2ti0` · **builds on:** `c9882254b` (this checkpoint is the commit after it)

## Just done
pre-release: DG1: bets.json saved at most every 4 s, OddsPapi Free cache deleted and never loaded, no bids.json.gz reads while engine desk is live (versionCode 453, v1.0.113)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.113), then run: bash tools/record-release.sh v1.0.113 453 "DG1: bets.json saved at most every 4 s, OddsPapi Free cache deleted and never loaded, no bids.json.gz reads while engine desk is live"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  c9882254b ckpt 4483: pre-ship: DG1: bets.json saved at most every 4 s, OddsPapi Free cache delet
  d4216bb86 ckpt 4482: pre-ship: DG1: bets.json saved at most every 4 s, OddsPapi Free cache delet
  4b3f649d2 ckpt 4481: chat: DG1 diagnostics logged, answered
  a0ff17d31 ckpt 4480: WATCH+ANALYST cover 11-16: no change, engine healthy
  f66173dd6 ckpt 4479: v1.0.112 shipped; engine stop cleared, runner reset
  1b54b2b1e ckpt 4478: pre-release: Phone reads no Novig order books for bids while the engine pos
  fe1f9276a ckpt 4477: pre-ship: Phone reads no Novig order books for bids while the engine posts 
  7097f9504 ckpt 4476: CloudLink loop 4 stops reading Novig books while engine posts bids
  872e45d41 ckpt 4475: chat: v1.0.111 shipped, runner reset; live real, pregame kinds pruned
  5b0f80357 ckpt 4474: pre-release: Engine: keeps ordering after an unreadable fill; pregame auto-
```
