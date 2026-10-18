# CHECKPOINT 3673 — read me first, then TASKS.md

**Written:** 2026-10-18T20:52:42Z · **tests:** all 5 fast checks green
**Branch:** `claude/optimistic-curie-r3n3xj` · **builds on:** `cecb00cfb` (this checkpoint is the commit after it)

## Just done
pre-release: v0.117.5: Live tab dashboard: every socket's state and last push, SX Bet health, the engine's live lane (versionCode 338, v0.117.5)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.117.5), then run: bash tools/record-release.sh v0.117.5 338 "v0.117.5: Live tab dashboard: every socket's state and last push, SX Bet health, the engine's live lane"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  cecb00cfb ckpt 3672: Live dashboard card, v0.117.5 bump
  6e13a48a1 ckpt 3671: v0.117.4 released+recorded, engine restarted
  e9c88a640 ckpt 3670: pre-release: v0.117.4: the engine asks Pinnodds' REST board first for Pinna
  6b55172b0 ckpt 3669: recorded v0.117.3; bumped 0.117.4 (337) for Pinnodds-first
  2dde3381b ckpt 3668: TCL121 live dashboard written
  46874068e ckpt 3667: answered SX status; TCL119/120 written
  0bb6307a7 ckpt 3666: pre-release: Engine asks Pinnodds REST board first for Pinnacle, PinnWire b
  796dcc1ed ckpt 3665: Pinnodds REST first in engine, pinnapi off
  7d889e28b ckpt 3664: pinnapi-first order + test + engine usePinnapi=true
  c6ca99eff ckpt 3663: wrote handoff-novig.md (active-memory handoff #1)
```
