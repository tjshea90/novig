# CHECKPOINT 3642 — read me first, then TASKS.md

**Written:** 2026-10-18T15:43:29Z · **tests:** all 5 fast checks green
**Branch:** `ccr-5a7eb2e9-bn8drh` · **builds on:** `727276bc3` (this checkpoint is the commit after it)

## Just done
pre-release: v0.117.1: refused Novig address replaced at once and unlimited, and no API is scanned until Novig accepts the runner; analysis 2026-10-18 (versionCode 334, v0.117.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.117.1), then run: bash tools/record-release.sh v0.117.1 334 "v0.117.1: refused Novig address replaced at once and unlimited, and no API is scanned until Novig accepts the runner; analysis 2026-10-18"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  965f1de5c ckpt 3641: TCL116: address gate before any API (probe, addressBlocked, test); TCL115 s
  a5dbae061 ckpt 3640: TCL115 tests green; analysis 2026-10-18 written; versionCode 334
  21e791f41 ckpt 3639: TCL114: runner replacement rule + full engine admin documented (ENGINE_ADMI
  508a81e88 ckpt 3638: TCL113 proven: engine_set -> phone adopts; bidDeployKelly 20 live, CHANGES.
  7d15807bb ckpt 3637: v0.117.0 released (TCL113)
  e8e327c13 ckpt 3636: pre-release: v0.117.0: Claude sessions may change any setting/switch except
  950079b3a ckpt 3635: pre-ship: v0.117.0: Claude sessions may change any setting/switch except ST
  fdb52658c ckpt 3634: TCL113 built: compile + unit test green
  c1d4a7bf7 ckpt 3633: TCL113: engine_set.py, CloudClaudeConfig + phone adopt, rules written; test
  bbcde0fd1 ckpt 3632: chat: TCL112 recorded + relayed
```

(13 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
