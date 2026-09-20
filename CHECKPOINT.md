# CHECKPOINT 179 — read me first, then TASKS.md

**Written:** 2026-09-20T16:14:27Z · **tests:** all 1 fast checks green
**Branch:** `claude/positive-ev-sports-research-n6jum5` · **builds on:** `58bbde3` (this checkpoint is the commit after it)

## Just done
Release v0.1.0 published: build, signature verification, server-side tag creation, and GitHub Release publish all green on the re-run. Recorded in BUILDLOG.md via tools/record-release.sh. This closes out the 'where is the apk' request end to end — Tj has a real, signed, installable APK.

## Do this next
Sample-data beta is shipped. Next real work is either going live (Tj's two action items from earlier: Odds API signup + contacting Novig about API access) or whatever Tj directs next.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  4cf8327 ckpt 168: First release.yml run: assembleRelease itself succeeded (proves the app builds
  afbb1cf ckpt 155: Tj switched the repo's default branch to main. Confirmed via the API, confirme
  013f762 ckpt 145: Tried to trigger release.yml — got a 404, workflow not found. Diagnosed why:
  a50ac5e ckpt 143: Switched Vigilant's release signing to match fantasy-football's precedent: gen
  200a6bd ckpt 137: Logged Tj's course-correction on release signing: checked Portfolio and fantas
  3bbf78a ckpt 134: Fixed a real regression from the ship.sh rewrite: tools/test_resume.sh's herme
  d115f2b ckpt 132: Built the real signed-release pipeline: generated Vigilant's permanent signing
  3fddc9e ckpt 126: Wrote Tj's 'where is the apk' request into TASKS.md. Confirmed this container'
  6f8d067 ckpt 123: CI confirmed fully green (run 35493330913): all tests across engine+data+app p
  49c1307 ckpt 96: Built the first real app code: 'Vigilant' — a 3-module Gradle project (engine
```

(9 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
