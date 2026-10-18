# CHECKPOINT 3642 — read me first, then TASKS.md

**Written:** 2026-10-18T15:02:17Z · **tests:** all 5 fast checks green
**Branch:** `claude/keen-keller-gi0bt9` · **builds on:** `cbc957b3f` (this checkpoint is the commit after it)

## Just done
reverted AddressWatch MIN_AGE to 4 min (loosening was blocked; awaiting Tj's confirm)

## Do this next
if Tj confirms: MIN_AGE 20s, MAX_IN_A_ROW 40, cooldown 5 min, update AddressWatchTest; then check pinnwire keys + SX status for Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M data/src/main/kotlin/com/tjshea/vigilant/data/cloud/AddressWatch.kt

## Last ten checkpoints
```
  394100891 ckpt 3641: WATCH 2026-10-18: engine healthy, no action
  17057ed16 ckpt 3640: TASKS.md triage: grouped 245 open items in research/TASKS_TRIAGE_2026-10-18
  8d52e1050 ckpt 3639: repo cleanup: ONE_TEAM.md index, branch audit, away.json cleared, CLAUDE.md
  7ca961608 ckpt 3638: merged origin/main into branch; kept tj Gmail name; test_coord 35/35 green
  7d15807bb ckpt 3637: v0.117.0 released (TCL113)
  e8e327c13 ckpt 3636: pre-release: v0.117.0: Claude sessions may change any setting/switch except
  950079b3a ckpt 3635: pre-ship: v0.117.0: Claude sessions may change any setting/switch except ST
  fdb52658c ckpt 3634: TCL113 built: compile + unit test green
  c1d4a7bf7 ckpt 3633: TCL113: engine_set.py, CloudClaudeConfig + phone adopt, rules written; test
  bbcde0fd1 ckpt 3632: chat: TCL112 recorded + relayed
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
