# CHECKPOINT 3768 — read me first, then TASKS.md

**Written:** 2026-10-19T20:41:47Z · **tests:** all 6 fast checks green
**Branch:** `claude/optimistic-curie-nfy0aq` · **builds on:** `f76ccb6cc` (this checkpoint is the commit after it)

## Just done
pre-release: v1.0.8: Engine > APIs lights (green/amber/grey, tap for the error), Test now and Try again per API; cross-exchange hedge paper journal (Novig + Kalshi/SX) (versionCode 348, v1.0.8)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.8), then run: bash tools/record-release.sh v1.0.8 348 "v1.0.8: Engine > APIs lights (green/amber/grey, tap for the error), Test now and Try again per API; cross-exchange hedge paper journal (Novig + Kalshi/SX)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  f76ccb6cc ckpt 3767: pre-ship: v1.0.8: Engine > APIs lights (green/amber/grey, tap for the error
  e5ccbab78 ckpt 3766: CH23b Try again + CH22b cross-exchange paper journal built, tests green
  805ba145e ckpt 3765: CH23a + Test now built on Engine>APIs cards (compiles, not shipped)
  fae17eb08 ckpt 3764: v1.0.7 released + recorded (SC1 settle fix); cleanup done
  887e7666a ckpt 3763: pre-release: v1.0.7: the engine waits 20 s for your settings edits to settl
  6e1d35722 ckpt 3762: pre-ship: v1.0.7: the engine waits 20 s for your settings edits to settle b
  9eada62c3 ckpt 3761: handoff to mirage written (TASKS.md HANDOFF TO MIRAGE + board note); CH24b 
  ea8cd737f ckpt 3760: added delete-old-releases.yml (APK releases only, keep newest 3 + v0.17.0, 
  74f7f5c20 ckpt 3759: added delete-stale-branches.yml (manual one-shot, 71 listed session branche
  a1a0f2073 ckpt 3758: merged mirage's CH24a (watchdog unreadable-branch fix) into my branch, self
```
