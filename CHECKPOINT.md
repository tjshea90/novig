# CHECKPOINT 2611 — read me first, then TASKS.md

**Written:** 2026-10-06T03:21:43Z · **tests:** all 3 fast checks green
**Branch:** `ccr-3436e911-cyln4u` · **builds on:** `ccff3d0c` (this checkpoint is the commit after it)

## Just done
pre-release: v0.70.0: real-money burst trader behind the recorder (OFF by default; locked until the recorder's proof on this phone, then your switch with a confirmation): both legs as one batch of two immediate-or-cancel orders, your stake/game/day/loss limits, halts on a lost answer or two legs held alone, Resume button; trade journal in Diagnostics and the share file (versionCode 118, v0.70.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.70.0), then run: bash tools/record-release.sh v0.70.0 118 "v0.70.0: real-money burst trader behind the recorder (OFF by default; locked until the recorder's proof on this phone, then your switch with a confirmation): both legs as one batch of two immediate-or-cancel orders, your stake/game/day/loss limits, halts on a lost answer or two legs held alone, Resume button; trade journal in Diagnostics and the share file"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  ccff3d0c ckpt 2610: executor done: full floor 2,289 passed (23 skipped); TASKS CV1 ticked, NOVIG
  0cad4cb3 ckpt 2609: burst trader wired into the app (adapter, rules, gate from BurstTradeGate + 
  a37f24dd ckpt 2608: v0.69.0 released + recorded (run green, Release up); BurstTrader core + 16 t
  d561774b ckpt 2607: pre-release: v0.69.0: live burst recorder (no orders): watches live games' l
  5a183237 ckpt 2606: v0.69.0 burst recorder: full floor green (2,282), TASKS/NOVIG_API updated, v
  46753e50 ckpt 2605: score-burst recorder built: ladders, covers, windows, paper trader, delays, 
  49db12ad ckpt 2604: Tj asks for a no-orders score-burst recorder in the app and whether it works
  9bf6ef80 ckpt 2603: answered Tj: 'Any time' doesn't widen Low API usage (mode's own 6 h rule); t
  75d489bf ckpt 2602: v0.68.1 RELEASED and recorded (run 122); main merged with one inbox-only com
  444974be ckpt 2601: pre-release: v0.68.1: Low API usage bids stay up (Auto pace: a scan starts b
```
