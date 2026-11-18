# CHECKPOINT 4649 — read me first, then TASKS.md

**Written:** 2026-11-18T05:31:02Z · **tests:** all 10 fast checks green
**Branch:** `ccr-f4518318-aidjgt` · **builds on:** `92d776b01` (this checkpoint is the commit after it)

## Just done
pre-release: MKV: parlay maker results in the app (Settled parlays tile, 24 h loss stop status), live at small limits, game lines only (versionCode 478, v1.0.138)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.138), then run: bash tools/record-release.sh v1.0.138 478 "MKV: parlay maker results in the app (Settled parlays tile, 24 h loss stop status), live at small limits, game lines only"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  92d776b01 ckpt 4648: pre-ship: MKV: parlay maker results in the app (Settled parlays tile, 24 h 
  7b408180b ckpt 4647: MK7: parlay maker LIVE since 04:50 UTC 2026-11-18 (Tj: 'Live, small limits'
  b8e884a27 ckpt 4646: engine reset for PropLine EV sweep; DG3 live lag not yet diagnosed
  4c681795a ckpt 4645: DG2 tests green; DG3 logged
  4f9d09d60 ckpt 4644: DG2: PropLine EV sweep on, phone bets kept 8 days
  363ba23cc ckpt 4643: DG2 logged
  a23a545ca ckpt 4642: chat: triaged diagnostics report DG1
  3891f24bb ckpt 4641: MKV3: LIVE prerequisites: 24 h realized-loss stop from Novig's executions (
  09268ea2c ckpt 4640: MKV: v1.0.137 released (recorded); MKV2 game-lines-only maker + 6% return d
  f70eb1882 ckpt 4639: MKV2: parlays with a player prop are not quoted or counted (market pays +6.
```
