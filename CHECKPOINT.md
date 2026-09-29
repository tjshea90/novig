# CHECKPOINT 2037 — read me first, then TASKS.md

**Written:** 2026-09-29T01:22:23Z · **tests:** all 2 fast checks green
**Branch:** `ccr-690067b2-r33qn5` · **builds on:** `757eeb61` (this checkpoint is the commit after it)

## Just done
Z3-Z5 done: setup script verified + hardened (never fails, build-tools 35, --prewarm with 250 s budget: first floor 106 s vs 210 s cold), tools/test.sh compact runner + gradle_summary check, data tests parallel (71->38 s), live switches as inputs; BRIEF trap 6 (one-line env script, dl.google.com), RESEARCH §33.6; bumped to v0.19.7 (code 42)

## Do this next
Z6: bash ship.sh, wait for CI green on main, trigger release.yml, confirm v0.19.7, record-release, send Tj the link + answers (skills travel with the repo; per account: setup-script line + dl.google.com)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  fc37a770 ckpt 2029: Z4/Z5 in progress: setup-android.sh never fails a session start (WARN + exit
  0f7eae8e ckpt 2015: Z1: Tj's decisions recorded (public keystore fine; no futures) in BRIEF/TASK
  914f38a5 ckpt 2011: Recorded Tj's request (keys public is fine; skills across accounts?; futures
  76da9a11 ckpt 2009: Y6 done: all checks green; PROMPT_AUDIT.md marked applied, patch removed; Y1
  8300accc ckpt 2007: Y3 + Y5 done: stale comments/labels fixed (engine:test 39/0), six stale TASK
  1cf5c103 ckpt 2004: Y2 + Y4: flags settled in BRIEF.md (F1-F7, F10, F11 Moto G 2026 specs; F9 in
  5936e866 ckpt 2000: Y1: applied PROMPT_AUDIT.patch (A1-A11 CLAUDE.md/briefing scripts incl. test
  fca73b59 ckpt 1998: Recorded Tj's request (fix everything fixable without breaking anything; use
  08a2ad3c ckpt 1996: X2-X3 done: Chris Banes' four Compose/coroutine skills in .claude/skills (un
  f3085ec4 ckpt 1992: X1 done: prompt audit report PROMPT_AUDIT.md + PROMPT_AUDIT.patch (21 propos
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
