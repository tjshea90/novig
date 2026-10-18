<<<<<<< HEAD
# CHECKPOINT 3644 — read me first, then TASKS.md

**Written:** 2026-10-18T15:48:43Z · **tests:** all 5 fast checks green
**Branch:** `ccr-5a7eb2e9-bn8drh` · **builds on:** `59fa89b6e` (this checkpoint is the commit after it)

## Just done
pre-release: v0.117.1: refused Novig address replaced at once (no scan on any API until Novig accepts the runner); SX Bet mapping and Sam Houston match fix (versionCode 334, v0.117.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.117.1), then run: bash tools/record-release.sh v0.117.1 334 "v0.117.1: refused Novig address replaced at once (no scan on any API until Novig accepts the runner); SX Bet mapping and Sam Houston match fix"
=======
# CHECKPOINT 3642 — read me first, then TASKS.md

**Written:** 2026-10-18T15:02:17Z · **tests:** all 5 fast checks green
**Branch:** `claude/keen-keller-gi0bt9` · **builds on:** `cbc957b3f` (this checkpoint is the commit after it)

## Just done
reverted AddressWatch MIN_AGE to 4 min (loosening was blocked; awaiting Tj's confirm)

## Do this next
if Tj confirms: MIN_AGE 20s, MAX_IN_A_ROW 40, cooldown 5 min, update AddressWatchTest; then check pinnwire keys + SX status for Tj
>>>>>>> origin/main

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
<<<<<<< HEAD

## Last ten checkpoints
```
  59fa89b6e ckpt 3643: TCL117: SX full mapping doc, real-pair test, TeamMatcher Sam Houston fix
  229e6f635 ckpt 3642: pre-release: v0.117.1: refused Novig address replaced at once and unlimited
  965f1de5c ckpt 3641: TCL116: address gate before any API (probe, addressBlocked, test); TCL115 s
  a5dbae061 ckpt 3640: TCL115 tests green; analysis 2026-10-18 written; versionCode 334
  21e791f41 ckpt 3639: TCL114: runner replacement rule + full engine admin documented (ENGINE_ADMI
  508a81e88 ckpt 3638: TCL113 proven: engine_set -> phone adopts; bidDeployKelly 20 live, CHANGES.
=======
     M data/src/main/kotlin/com/tjshea/vigilant/data/cloud/AddressWatch.kt

## Last ten checkpoints
```
  394100891 ckpt 3641: WATCH 2026-10-18: engine healthy, no action
  17057ed16 ckpt 3640: TASKS.md triage: grouped 245 open items in research/TASKS_TRIAGE_2026-10-18
  8d52e1050 ckpt 3639: repo cleanup: ONE_TEAM.md index, branch audit, away.json cleared, CLAUDE.md
  7ca961608 ckpt 3638: merged origin/main into branch; kept tj Gmail name; test_coord 35/35 green
>>>>>>> origin/main
  7d15807bb ckpt 3637: v0.117.0 released (TCL113)
  e8e327c13 ckpt 3636: pre-release: v0.117.0: Claude sessions may change any setting/switch except
  950079b3a ckpt 3635: pre-ship: v0.117.0: Claude sessions may change any setting/switch except ST
  fdb52658c ckpt 3634: TCL113 built: compile + unit test green
<<<<<<< HEAD
=======
  c1d4a7bf7 ckpt 3633: TCL113: engine_set.py, CloudClaudeConfig + phone adopt, rules written; test
  bbcde0fd1 ckpt 3632: chat: TCL112 recorded + relayed
>>>>>>> origin/main
```
