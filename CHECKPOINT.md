# CHECKPOINT 145 — read me first, then TASKS.md

**Written:** 2026-09-20T06:52:23Z · **tests:** all 1 fast checks green
**Branch:** `claude/positive-ev-sports-research-n6jum5` · **builds on:** `50659ae` (this checkpoint is the commit after it)

## Just done
Tried to trigger release.yml — got a 404, workflow not found. Diagnosed why: this repo's actual GitHub default branch was never changed to main, it's still claude/novig-checkpoint-tests-qqgnnb (an old session's branch, from repo creation). GitHub only registers workflow_dispatch workflows for API triggering once they exist on the real default branch, not just on main/the working branch. Confirmed main itself IS current and DOES have release.yml (push.sh's fast-forward is working correctly) — the gap is purely the GitHub repo-settings default_branch field. Tried to fix it via the API myself; blocked outright by the auto-mode permission classifier as a repo-admin action, told to stop and ask. Recorded this as a 4th build trap in BRIEF.md so it's never re-diagnosed from scratch.

## Do this next
Ask Tj to change the repo's default branch to main himself (GitHub repo Settings -> General -> Default branch — a few seconds), or explicitly authorize Claude to do it via the API. Cannot proceed with triggering any workflow_dispatch workflow until this is fixed.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  a50ac5e ckpt 143: Switched Vigilant's release signing to match fantasy-football's precedent: gen
  200a6bd ckpt 137: Logged Tj's course-correction on release signing: checked Portfolio and fantas
  3bbf78a ckpt 134: Fixed a real regression from the ship.sh rewrite: tools/test_resume.sh's herme
  d115f2b ckpt 132: Built the real signed-release pipeline: generated Vigilant's permanent signing
  3fddc9e ckpt 126: Wrote Tj's 'where is the apk' request into TASKS.md. Confirmed this container'
  6f8d067 ckpt 123: CI confirmed fully green (run 35493330913): all tests across engine+data+app p
  49c1307 ckpt 96: Built the first real app code: 'Vigilant' — a 3-module Gradle project (engine
  3edb9b3 ckpt 29: Wrote Tj's 'begin basic coding' request into TASKS.md (raw message already capt
  c361581 ckpt 26: Deep-dived Odds Assist Pro against Novig, hands-on (not just secondhand): loade
  4ce2d00 ckpt 21: Wrote Tj's Odds Assist Pro deep-dive request into TASKS.md (raw message already
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
