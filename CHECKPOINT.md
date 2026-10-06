# CHECKPOINT 2613 — read me first, then TASKS.md

**Written:** 2026-10-06T03:36:16Z · **tests:** all 3 fast checks green
**Branch:** `ccr-3436e911-cyln4u` · **builds on:** `4d6b4374` (this checkpoint is the commit after it)

## Just done
pre-release: v0.70.1: the burst trader is judged league by league (a league trades only once its own recorded windows prove it; Settings names the leagues that did), on top of v0.70.0's real-money trader (OFF by default, locked until the recorder's proof, your switch with a confirmation, IOC batch of two legs, your limits, halts, Resume) (versionCode 119, v0.70.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.70.1), then run: bash tools/record-release.sh v0.70.1 119 "v0.70.1: the burst trader is judged league by league (a league trades only once its own recorded windows prove it; Settings names the leagues that did), on top of v0.70.0's real-money trader (OFF by default, locked until the recorder's proof, your switch with a confirmation, IOC batch of two legs, your limits, halts, Resume)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  4d6b4374 ckpt 2612: v0.70.1: per-league proof for the trader (a league trades only on its own pr
  73c148b8 ckpt 2611: pre-release: v0.70.0: real-money burst trader behind the recorder (OFF by de
  ccff3d0c ckpt 2610: executor done: full floor 2,289 passed (23 skipped); TASKS CV1 ticked, NOVIG
  0cad4cb3 ckpt 2609: burst trader wired into the app (adapter, rules, gate from BurstTradeGate + 
  a37f24dd ckpt 2608: v0.69.0 released + recorded (run green, Release up); BurstTrader core + 16 t
  d561774b ckpt 2607: pre-release: v0.69.0: live burst recorder (no orders): watches live games' l
  5a183237 ckpt 2606: v0.69.0 burst recorder: full floor green (2,282), TASKS/NOVIG_API updated, v
  46753e50 ckpt 2605: score-burst recorder built: ladders, covers, windows, paper trader, delays, 
  49db12ad ckpt 2604: Tj asks for a no-orders score-burst recorder in the app and whether it works
  9bf6ef80 ckpt 2603: answered Tj: 'Any time' doesn't widen Low API usage (mode's own 6 h rule); t
```
