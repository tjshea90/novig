# CHECKPOINT 4080 — read me first, then TASKS.md

**Written:** 2026-10-24T00:07:40Z · **tests:** all 9 fast checks green
**Branch:** `ccr-f7c47823-ez8xwu` · **builds on:** `2c466ddbb` (this checkpoint is the commit after it)

## Just done
pre-release: FX full tests: Kalshi bid cancels fixed (V2 needs the market), Kalshi lane re-reads before judging, SharpAPI/SX streams reconnect on a server close, digest shows the engine's graded days + star check + API value, tab bar and Settings text fixes (versionCode 406, v1.0.66)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.66), then run: bash tools/record-release.sh v1.0.66 406 "FX full tests: Kalshi bid cancels fixed (V2 needs the market), Kalshi lane re-reads before judging, SharpAPI/SX streams reconnect on a server close, digest shows the engine's graded days + star check + API value, tab bar and Settings text fixes"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  40c94935b ckpt 4079: FX3/FX4 written: research/star_rating_review_2026-10-23.md, research/api_ve
  8e828381f ckpt 4078: FX2/FX3: star check rerun read (recorded lines only: ROI rises with stars, 
  d492cbeef ckpt 4077: FX2: SharpAPI and SX Bet streams ignored server closes (no onClosing): a de
  59e69cd79 ckpt 4076: FX2: study splits capped at 25 groups (engine digest's Books agreeing had h
  5e21abe12 ckpt 4075: FX2 engine bugs from the live log: (1) Kalshi bid cancels sent by id alone 
  72184f30c ckpt 4074: FX2: StudyExport played-days-first (test fails on old code), Review day bud
  b8f282792 ckpt 4073: FX1 floor green (4,192 tests, exit 0), 25 py selftests ok, 134 screenshots 
  8c5eba4e6 ckpt 4072: FX: full-test request written into TASKS.md (FX1-FX5), claimed
  1bb4f757f ckpt 4071: ANALYST 2026-10-23: no rule change
  3e0dff430 ckpt 4070: pre-release: ST2: stars/soonest sort works on the merged Novig+Kalshi list 
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
