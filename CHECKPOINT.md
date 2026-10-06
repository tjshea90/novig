# CHECKPOINT 2612 — read me first, then TASKS.md

**Written:** 2026-10-06T03:30:23Z · **tests:** all 3 fast checks green
**Branch:** `ccr-3436e911-cyln4u` · **builds on:** `5cde7967` (this checkpoint is the commit after it)

## Just done
v0.70.1: per-league proof for the trader (a league trades only on its own proof), RESEARCH 95/96 written (MNF: ESPN 40.7 s late, Novig 16.1 s after a play, 8 bursts = 1.1% of payout; other sports unproven), TASKS ticked, v0.70.0 released+recorded

## Do this next
wait CI green on this commit, bash ship.sh, release.yml, confirm get_release_by_tag v0.70.1, record-release, send Tj both Release links + the plain answers (sources, burst, other sports, trader gating)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  73c148b8 ckpt 2611: pre-release: v0.70.0: real-money burst trader behind the recorder (OFF by de
  ccff3d0c ckpt 2610: executor done: full floor 2,289 passed (23 skipped); TASKS CV1 ticked, NOVIG
  0cad4cb3 ckpt 2609: burst trader wired into the app (adapter, rules, gate from BurstTradeGate + 
  a37f24dd ckpt 2608: v0.69.0 released + recorded (run green, Release up); BurstTrader core + 16 t
  d561774b ckpt 2607: pre-release: v0.69.0: live burst recorder (no orders): watches live games' l
  5a183237 ckpt 2606: v0.69.0 burst recorder: full floor green (2,282), TASKS/NOVIG_API updated, v
  46753e50 ckpt 2605: score-burst recorder built: ladders, covers, windows, paper trader, delays, 
  49db12ad ckpt 2604: Tj asks for a no-orders score-burst recorder in the app and whether it works
  9bf6ef80 ckpt 2603: answered Tj: 'Any time' doesn't widen Low API usage (mode's own 6 h rule); t
  75d489bf ckpt 2602: v0.68.1 RELEASED and recorded (run 122); main merged with one inbox-only com
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
