# CHECKPOINT 3719 — read me first, then TASKS.md

**Written:** 2026-10-19T03:39:12Z · **tests:** all 5 fast checks green
**Branch:** `claude/optimistic-curie-gntx94` · **builds on:** `47ec8bfb6` (this checkpoint is the commit after it)

## Just done
pre-release: v1.0.3: Live light middle tier (model 80-89% sure, 5% edge, real money at 10-cent stakes) (versionCode 343, v1.0.3)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.3), then run: bash tools/record-release.sh v1.0.3 343 "v1.0.3: Live light middle tier (model 80-89% sure, 5% edge, real money at 10-cent stakes)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  47ec8bfb6 ckpt 3718: pre-ship: v1.0.3: Live light middle tier (model 80-89% sure, 5% edge, real 
  1f2323142 ckpt 3717: CH10 resolved, CH12 needs diagnostics file, CH13=CH7 done
  129a870c4 ckpt 3716: CH15 no soccer verified; CH9 soccer dropped
  feb6fa2ee ckpt 3715: chat session end: replies sent, work queued
  7b6619bca ckpt 3714: CH6 traced: no extra key usage
  9b5aaa91c ckpt 3713: CH14 queued
  229cb7142 ckpt 3712: CH11-13 queued, replies sent
  a538777e4 ckpt 3711: answered old chat msgs; CH10 queued
  ed8c7d614 ckpt 3710: CH9 SX audit: names match; props not on SX; next check ML mainLine skip + s
  ed0c1aa71 ckpt 3709: CH8 sx/polymarket thin coverage answered
```
