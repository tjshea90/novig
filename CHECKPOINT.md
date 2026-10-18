# CHECKPOINT 3647 — read me first, then TASKS.md

**Written:** 2026-10-18T16:18:48Z · **tests:** 1 RED: test_coord.sh (4 green)
**Branch:** `ccr-5a7eb2e9-bn8drh` · **builds on:** `86e96923a` (this checkpoint is the commit after it)

## Just done
pre-release: v0.117.2: SX Bet live feed (exchange mid with live scores) as a live-lane price source on the phone and the engine, paper until allowed; refused Novig address replaced at once (versionCode 334, v0.117.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.117.1), then run: bash tools/record-release.sh v0.117.1 334 "v0.117.2: SX Bet live feed (exchange mid with live scores) as a live-lane price source on the phone and the engine, paper until allowed; refused Novig address replaced at once"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  86e96923a ckpt 3646: pre-ship: v0.117.2: SX Bet live feed (exchange mid with live scores) as a l
  803af4e34 ckpt 3645: merged origin/main into branch (docs/tools only on main); v0.117.1 release 
  113525a94 ckpt 3644: pre-release: v0.117.1: refused Novig address replaced at once (no scan on a
  59fa89b6e ckpt 3643: TCL117: SX full mapping doc, real-pair test, TeamMatcher Sam Houston fix
  229e6f635 ckpt 3642: pre-release: v0.117.1: refused Novig address replaced at once and unlimited
  965f1de5c ckpt 3641: TCL116: address gate before any API (probe, addressBlocked, test); TCL115 s
  a5dbae061 ckpt 3640: TCL115 tests green; analysis 2026-10-18 written; versionCode 334
  21e791f41 ckpt 3639: TCL114: runner replacement rule + full engine admin documented (ENGINE_ADMI
  3a59130e8 ckpt 3642: reverted AddressWatch MIN_AGE to 4 min (loosening was blocked; awaiting Tj'
  394100891 ckpt 3641: WATCH 2026-10-18: engine healthy, no action
```
