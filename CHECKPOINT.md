# CHECKPOINT 4542 — read me first, then TASKS.md

**Written:** 2026-11-16T18:16:20Z · **tests:** all 10 fast checks green
**Branch:** `claude/optimistic-curie-mnnrec` · **builds on:** `9ec516842` (this checkpoint is the commit after it)

## Just done
pre-release: Diagnostics names which tab stutters and flags a slow screen with nothing running (versionCode 464, v1.0.124)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.124), then run: bash tools/record-release.sh v1.0.124 464 "Diagnostics names which tab stutters and flags a slow screen with nothing running"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  9ec516842 ckpt 4541: pre-ship: Diagnostics names which tab stutters and flags a slow screen with
  6f606270b ckpt 4540: PF3: per-tab frame meter + quiet-lag finding, full suite green
  fbe2e6712 ckpt 4539: PF3a: Advisor finding when the screen is slow with nothing running (FrameMe
  fb807eda8 ckpt 4538: PF3 logged; answered Tj chat (report uploads to GitHub; lag numbers from 18
  08fff40f2 ckpt 4537: PF2: reverted report reorder (tests pass)
  a30f9648d ckpt 4536: PF1 done (workflow crons off); PF2 audit: no cause found, need frames
  f5e81c869 ckpt 4535: PF1: PropLine free tier: Pro-only workflow schedules off; code already degr
  d1bb96e5c ckpt 4534: pre-release: Settled bets older than 3 days lose their heavy snapshot once 
  8514e029a ckpt 4533: pre-ship: Settled bets older than 3 days lose their heavy snapshot once on 
  61d9516a5 ckpt 4532: DG5 slimArchived built+tested
```
