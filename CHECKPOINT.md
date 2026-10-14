# CHECKPOINT 3203 — read me first, then TASKS.md

**Written:** 2026-10-14T01:44:53Z · **tests:** all 4 fast checks green
**Branch:** `ccr-147419fb-llvuq5` · **builds on:** `e570ee2da` (this checkpoint is the commit after it)

## Just done
pre-release: v0.98.1: the CNO list drops a bet the Bet sheet or the lowest fair says is not +EV even with no fresh price read; Diagnostics list why bids were skipped (wallet deployment) (versionCode 257, v0.98.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.98.1), then run: bash tools/record-release.sh v0.98.1 257 "v0.98.1: the CNO list drops a bet the Bet sheet or the lowest fair says is not +EV even with no fresh price read; Diagnostics list why bids were skipped (wallet deployment)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  e570ee2da ckpt 3202: pre-ship: v0.98.1: the CNO list drops a bet the Bet sheet or the lowest fai
  bcf471faa ckpt 3201: v0.98.0 released and recorded
  13cdd2f2f ckpt 3200: pre-release: v0.98.0: bids keep the wallet working (stakes scale to 1/4 Kel
  55278ff8e ckpt 3199: pre-ship: v0.98.0: bids keep the wallet working (stakes scale to 1/4 Kelly,
  150d1c5bf ckpt 3198: TWF5: diagnostics sockets, wallet deployment, stream phase timings, lab yie
  ccf9dce4a ckpt 3197: TWF4: bids keep the wallet working (stake scale to 1/4 Kelly, riskier highe
  4767443bc ckpt 3196: TWF2: public lookups slow on 429 + remembered, lab yields to scans, stream 
  2f4a16aaa ckpt 3195: TWF tasks written
  0b6098311 ckpt 3194: v0.97.3 released and recorded
  e4819e974 ckpt 3193: v0.97.2 recorded; v0.97.3 (BoltOdds classify memo) shipped, release trigger
```
