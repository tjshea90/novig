# CHECKPOINT 137 — read me first, then TASKS.md

**Written:** 2026-09-20T06:48:18Z · **tests:** all 1 fast checks green
**Branch:** `claude/positive-ev-sports-research-n6jum5` · **builds on:** `8fd8c73` (this checkpoint is the commit after it)

## Just done
Logged Tj's course-correction on release signing: checked Portfolio and fantasy-football directly rather than assume — fantasy-football signs with a committed debug-style keystore (well-known password, no secret needed) since it holds nothing sensitive; Portfolio's real workflow does use a secret. Tj wants the fantasy-football pattern for Vigilant. Updated TASKS.md with the plan; the earlier Secret-based keystore approach is now marked superseded.

## Do this next
Generate a new debug-style keystore for Vigilant, commit it directly to the repo, rewire app/build.gradle.kts and release.yml to sign with it (no secrets), correct BRIEF.md's now-stale keystore section, then actually trigger a real release this time since nothing should block it.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  3bbf78a ckpt 134: Fixed a real regression from the ship.sh rewrite: tools/test_resume.sh's herme
  d115f2b ckpt 132: Built the real signed-release pipeline: generated Vigilant's permanent signing
  3fddc9e ckpt 126: Wrote Tj's 'where is the apk' request into TASKS.md. Confirmed this container'
  6f8d067 ckpt 123: CI confirmed fully green (run 35493330913): all tests across engine+data+app p
  49c1307 ckpt 96: Built the first real app code: 'Vigilant' — a 3-module Gradle project (engine
  3edb9b3 ckpt 29: Wrote Tj's 'begin basic coding' request into TASKS.md (raw message already capt
  c361581 ckpt 26: Deep-dived Odds Assist Pro against Novig, hands-on (not just secondhand): loade
  4ce2d00 ckpt 21: Wrote Tj's Odds Assist Pro deep-dive request into TASKS.md (raw message already
  5787e5b ckpt 18: Deep research on positive-EV betting for Novig, written to RESEARCH.md (new per
  1c3d806 ckpt 15: Wrote Tj's positive-EV research request into TASKS.md in his own words as untic
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
