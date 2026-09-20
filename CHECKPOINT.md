# CHECKPOINT 143 — read me first, then TASKS.md

**Written:** 2026-09-20T06:50:38Z · **tests:** all 1 fast checks green
**Branch:** `claude/positive-ev-sports-research-n6jum5` · **builds on:** `661db07` (this checkpoint is the commit after it)

## Just done
Switched Vigilant's release signing to match fantasy-football's precedent: generated a new keystore (alias 'vigilant', well-known debug password, committed directly at app/keystore/vigilant-debug.jks via a .gitignore negation), rewired app/build.gradle.kts's signingConfig to read it directly (no env vars), simplified release.yml to drop the secret-decode step and updated the expected fingerprint. Corrected BRIEF.md's keystore section to reflect this as the current model, explain why (Tj's explicit instruction, checked against both other repos rather than assumed), record the new fingerprint, and flag the real tradeoff (forgeable signing, fine with no real credentials in the app yet — revisit once Novig API creds are wired in). Verified: engine+data's 54 tests still green, test_resume.sh's full 20-check hermetic suite still green.

## Do this next
Nothing blocks a real release now. Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green, run tools/record-release.sh v0.1.0 1 "first beta release", then send Tj the Release page link as plain tappable text.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  200a6bd ckpt 137: Logged Tj's course-correction on release signing: checked Portfolio and fantas
  3bbf78a ckpt 134: Fixed a real regression from the ship.sh rewrite: tools/test_resume.sh's herme
  d115f2b ckpt 132: Built the real signed-release pipeline: generated Vigilant's permanent signing
  3fddc9e ckpt 126: Wrote Tj's 'where is the apk' request into TASKS.md. Confirmed this container'
  6f8d067 ckpt 123: CI confirmed fully green (run 35493330913): all tests across engine+data+app p
  49c1307 ckpt 96: Built the first real app code: 'Vigilant' — a 3-module Gradle project (engine
  3edb9b3 ckpt 29: Wrote Tj's 'begin basic coding' request into TASKS.md (raw message already capt
  c361581 ckpt 26: Deep-dived Odds Assist Pro against Novig, hands-on (not just secondhand): loade
  4ce2d00 ckpt 21: Wrote Tj's Odds Assist Pro deep-dive request into TASKS.md (raw message already
  5787e5b ckpt 18: Deep research on positive-EV betting for Novig, written to RESEARCH.md (new per
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
