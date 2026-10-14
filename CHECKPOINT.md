# CHECKPOINT 3200 — read me first, then TASKS.md

**Written:** 2026-10-14T01:04:58Z · **tests:** all 4 fast checks green
**Branch:** `ccr-b57fa2a2-ftkkrm` · **builds on:** `55278ff8e` (this checkpoint is the commit after it)

## Just done
pre-release: v0.98.0: bids keep the wallet working (stakes scale to 1/4 Kelly, riskier higher-edge bids fill the gap), the paper lab waits for scans, Novig lookups slow on a 429 and are remembered, the streaming engine rests when BoltOdds' socket is off, Diagnostics gain SOCKETS, WALLET DEPLOYMENT and phase timings (versionCode 256, v0.98.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.98.0), then run: bash tools/record-release.sh v0.98.0 256 "v0.98.0: bids keep the wallet working (stakes scale to 1/4 Kelly, riskier higher-edge bids fill the gap), the paper lab waits for scans, Novig lookups slow on a 429 and are remembered, the streaming engine rests when BoltOdds' socket is off, Diagnostics gain SOCKETS, WALLET DEPLOYMENT and phase timings"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  55278ff8e ckpt 3199: pre-ship: v0.98.0: bids keep the wallet working (stakes scale to 1/4 Kelly,
  150d1c5bf ckpt 3198: TWF5: diagnostics sockets, wallet deployment, stream phase timings, lab yie
  ccf9dce4a ckpt 3197: TWF4: bids keep the wallet working (stake scale to 1/4 Kelly, riskier highe
  4767443bc ckpt 3196: TWF2: public lookups slow on 429 + remembered, lab yields to scans, stream 
  2f4a16aaa ckpt 3195: TWF tasks written
  0b6098311 ckpt 3194: v0.97.3 released and recorded
  e4819e974 ckpt 3193: v0.97.2 recorded; v0.97.3 (BoltOdds classify memo) shipped, release trigger
  4950d974f ckpt 3192: pre-release: v0.97.3: BoltOdds passes classify each market name once instea
  dc824e142 ckpt 3191: pre-ship: v0.97.3: BoltOdds passes classify each market name once instead o
  2d3b1c981 ckpt 3190: pre-release: v0.97.2: Kalshi socket now counts every subscribed market (ok 
```
