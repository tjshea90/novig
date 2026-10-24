# CHECKPOINT 4111 — read me first, then TASKS.md

**Written:** 2026-10-24T06:59:33Z · **tests:** all 9 fast checks green
**Branch:** `ccr-305d3f7a-i2z8ja` · **builds on:** `67fb5137d` (this checkpoint is the commit after it)

## Just done
pre-release: SX: stars are the bet type's proof at Novig (CLV against Novig's own close of Vigilant/CNO graded lines + point-in-time backtest); 0 = no trusted edge incl. types losing on both sources, 1 = +EV unproven, up to 5 = proven at 99%; weekly star-evidence refresh (versionCode 411, v1.0.71)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.71), then run: bash tools/record-release.sh v1.0.71 411 "SX: stars are the bet type's proof at Novig (CLV against Novig's own close of Vigilant/CNO graded lines + point-in-time backtest); 0 = no trusted edge incl. types losing on both sources, 1 = +EV unproven, up to 5 = proven at 99%; weekly star-evidence refresh"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  67fb5137d ckpt 4110: pre-ship: SX: stars are the bet type's proof at Novig (CLV against Novig's 
  6a94c9011 ckpt 4109: SX5 done: Scale.SX shipped in code (type's proof at Novig from star_evidenc
  0ced35d7e ckpt 4108: SX findings 4-5: Novig's own close is the yardstick (ROI = 0.82 x CLV_novig
  14afb9504 ckpt 4108: PL-OA: PropLine book accuracy report read and written up
  398215421 ckpt 4107: propline accuracy task: try each comma-separated key
  43909e77f ckpt 4106: propline.yml task accuracy (PL-OA)
  cfe7f1b9d ckpt 4105: PropLine openapi saved, task PL-OA written
  b7e8e5b26 ckpt 4106: SX findings 2-3 written (point-in-time multiplicative edge lost; favourite-
  7617623f2 ckpt 4105: SX finding 2 (multiplicative devig): +EV Novig lines lost ROI -5..-6% at ev
  49c19f9e1 ckpt 4104: pre-release: 1xBet under any feed spelling (1xbet/onexbet) is one company, 
```
