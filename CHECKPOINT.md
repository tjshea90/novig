# CHECKPOINT 2038 — read me first, then TASKS.md

**Written:** 2026-09-29T01:23:16Z · **tests:** all 2 fast checks green
**Branch:** `ccr-690067b2-r33qn5` · **builds on:** `9b9532e8` (this checkpoint is the commit after it)

## Just done
pre-release: v0.19.7: CNO's list leaves out futures and awards (games only; the CNO tab counts them as "futures (not a game)"). Dev: cloud setup script never blocks a session, installs the build-tools the build uses and can pre-download every dependency; compact test runner; data tests in parallel; live-test switches tracked by Gradle. 761 tests (versionCode 42, v0.19.7)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.19.7), then run: bash tools/record-release.sh v0.19.7 42 "v0.19.7: CNO's list leaves out futures and awards (games only; the CNO tab counts them as "futures (not a game)"). Dev: cloud setup script never blocks a session, installs the build-tools the build uses and can pre-download every dependency; compact test runner; data tests in parallel; live-test switches tracked by Gradle. 761 tests"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  9b9532e8 ckpt 2037: Z3-Z5 done: setup script verified + hardened (never fails, build-tools 35, -
  fc37a770 ckpt 2029: Z4/Z5 in progress: setup-android.sh never fails a session start (WARN + exit
  0f7eae8e ckpt 2015: Z1: Tj's decisions recorded (public keystore fine; no futures) in BRIEF/TASK
  914f38a5 ckpt 2011: Recorded Tj's request (keys public is fine; skills across accounts?; futures
  76da9a11 ckpt 2009: Y6 done: all checks green; PROMPT_AUDIT.md marked applied, patch removed; Y1
  8300accc ckpt 2007: Y3 + Y5 done: stale comments/labels fixed (engine:test 39/0), six stale TASK
  1cf5c103 ckpt 2004: Y2 + Y4: flags settled in BRIEF.md (F1-F7, F10, F11 Moto G 2026 specs; F9 in
  5936e866 ckpt 2000: Y1: applied PROMPT_AUDIT.patch (A1-A11 CLAUDE.md/briefing scripts incl. test
  fca73b59 ckpt 1998: Recorded Tj's request (fix everything fixable without breaking anything; use
  08a2ad3c ckpt 1996: X2-X3 done: Chris Banes' four Compose/coroutine skills in .claude/skills (un
```
