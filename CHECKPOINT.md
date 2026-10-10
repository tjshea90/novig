# CHECKPOINT 2875 — read me first, then TASKS.md

**Written:** 2026-10-10T16:12:59Z · **tests:** all 4 fast checks green
**Branch:** `ccr-3fba2f23-lerg26` · **builds on:** `e022e1ef` (this checkpoint is the commit after it)

## Just done
pre-release: v0.87.0: live autopilot (taker + bids together), More fills preset, fill-the-wallet limits, score-or-stale trigger, why-so-few-fills report (versionCode 171, v0.87.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.87.0), then run: bash tools/record-release.sh v0.87.0 171 "v0.87.0: live autopilot (taker + bids together), More fills preset, fill-the-wallet limits, score-or-stale trigger, why-so-few-fills report"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  e022e1ef ckpt 2874: TL Phase A UI+tests green; RESEARCH §125 written
  1a98f6da ckpt 2873: TL3 Phase A core: FILL preset, fillWallet limits, EITHER trigger, LiveAutopi
  bec190df ckpt 2872: TL1 evidence read; plan A1-A5/B
  958f2b71 ckpt 2871: TL written
  83880fa4 ckpt 2870: v0.86.1 released and recorded; every item of the queue done
  114e3e64 ckpt 2869: pre-release: v0.86.1: trap guard hours split (auto-bet vs bids), profit grap
  c35f9aee ckpt 2868: pre-ship: v0.86.1: trap guard hours split (auto-bet vs bids), profit graph w
  2fe38e40 ckpt 2867: TK2 profit graph (ranges, pinch zoom, scroll, full screen) done; TJ4 memo wr
  08dfe7fa ckpt 2866: TK1 trap guard split done (bids vs auto-bet), TJ2 HeapCensus + JsonSplit (SG
  9f1d520a ckpt 2865: TJ written; release v0.86.0 triggered (run 38061298709)
```
