# CHECKPOINT 2029 — read me first, then TASKS.md

**Written:** 2026-09-29T01:07:44Z · **tests:** all 2 fast checks green
**Branch:** `ccr-690067b2-r33qn5` · **builds on:** `8e021792` (this checkpoint is the commit after it)

## Just done
Z4/Z5 in progress: setup-android.sh never fails a session start (WARN + exit 0), installs build-tools 35.0.0 (AGP 8.13 default), --prewarm builds a throwaway clone + one Robolectric test (cold: whole script 210 s, prewarm 197 s); tools/test.sh compact runner (+ tools/gradle_summary.py, fast check tools/test_gradle_summary.sh, fails on a broken summarizer); ship.sh/CLAUDE.md/test-protocols use it; data tests: VIGILANT_* env vars as inputs + maxParallelForks; Z1/Z2 ticked; measured cold floor 210 s vs warm 110 s, 0 x 429

## Do this next
Finish Z4: pre-warm timing (warm in-process run bcw0zlttx), settle the cap (maybe Kotlin daemon + 240 s), quiet sdkmanager stderr; verify test.sh + parallel forks with a real run (data:test time, env-input rerun); BRIEF trap 6 + RESEARCH §33.6 write-up; then Z3 answer, Z6 ship v0.19.7 (code 42)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  0f7eae8e ckpt 2015: Z1: Tj's decisions recorded (public keystore fine; no futures) in BRIEF/TASK
  914f38a5 ckpt 2011: Recorded Tj's request (keys public is fine; skills across accounts?; futures
  76da9a11 ckpt 2009: Y6 done: all checks green; PROMPT_AUDIT.md marked applied, patch removed; Y1
  8300accc ckpt 2007: Y3 + Y5 done: stale comments/labels fixed (engine:test 39/0), six stale TASK
  1cf5c103 ckpt 2004: Y2 + Y4: flags settled in BRIEF.md (F1-F7, F10, F11 Moto G 2026 specs; F9 in
  5936e866 ckpt 2000: Y1: applied PROMPT_AUDIT.patch (A1-A11 CLAUDE.md/briefing scripts incl. test
  fca73b59 ckpt 1998: Recorded Tj's request (fix everything fixable without breaking anything; use
  08a2ad3c ckpt 1996: X2-X3 done: Chris Banes' four Compose/coroutine skills in .claude/skills (un
  f3085ec4 ckpt 1992: X1 done: prompt audit report PROMPT_AUDIT.md + PROMPT_AUDIT.patch (21 propos
  df0cd971 ckpt 608: Recorded Tj's request (run the prompt audit, add the Compose skills) as X1-X3
```

(13 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
