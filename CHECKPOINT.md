# CHECKPOINT 29 — read me first, then TASKS.md

**Written:** 2026-09-20T05:39:58Z · **tests:** all 1 fast checks green
**Branch:** `claude/positive-ev-sports-research-n6jum5` · **builds on:** `abae6f7` (this checkpoint is the commit after it)

## Just done
Wrote Tj's 'begin basic coding' request into TASKS.md (raw message already captured in INBOX.md). Confirmed local tooling: JDK 21 and Gradle 8.x present in this container, but no Android SDK/ANDROID_HOME — the Android app module's real compile verification will have to come from GitHub Actions CI, not this container, matching BRIEF.md's own 'not a build that happened inside this container' model. Picked app name 'Vigilant' (real word, hidden 'vig' pun, implies constant real-time watching — matches the real-time requirement) after a quick search turned up no collision with an existing betting-edge app of that name.

## Do this next
Build the app: toolchain decision + BRIEF.md update, Android Gradle scaffold, a plain-Kotlin devig/EV engine module with real unit tests (verifiable in this container), a data layer with sample-data + real-but-inert API client scaffolding for The Odds API and Novig's documented API, a basic Compose UI, and a CI workflow to get real Android-module build verification since this container can't do it locally. End by telling Tj plainly what he needs to do (Odds API signup, contacting Novig for credentials) — do not let that get lost.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  c361581 ckpt 26: Deep-dived Odds Assist Pro against Novig, hands-on (not just secondhand): loade
  4ce2d00 ckpt 21: Wrote Tj's Odds Assist Pro deep-dive request into TASKS.md (raw message already
  5787e5b ckpt 18: Deep research on positive-EV betting for Novig, written to RESEARCH.md (new per
  1c3d806 ckpt 15: Wrote Tj's positive-EV research request into TASKS.md in his own words as untic
  e870ad3 ckpt 12: Stood up the full checkpoint/handoff system for novig, adapted from fantasy-foo
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
