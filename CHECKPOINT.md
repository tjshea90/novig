# CHECKPOINT 132 — read me first, then TASKS.md

**Written:** 2026-09-20T06:40:47Z · **tests:** 1 RED: test_resume.sh (0 green)
**Branch:** `claude/positive-ev-sports-research-n6jum5` · **builds on:** `41a9c28` (this checkpoint is the commit after it)

## Just done
Built the real signed-release pipeline: generated Vigilant's permanent signing keystore (alias 'vigilant', 30yr validity, SHA-256 fingerprint recorded in BRIEF.md per its own keystore rule — password never recorded in-repo), sent the keystore file + password + base64 + setup instructions directly to Tj via SendUserFile (his own backup — this container's GitHub token is confirmed blocked from the Actions-secrets API, 403 from the proxy, so Claude cannot create the GitHub Secrets itself). Wired app/build.gradle.kts to read signing config from env vars (unsigned unless VIGILANT_KEYSTORE_PATH is set, which only release.yml sets). Wrote .github/workflows/release.yml: manual-trigger only, decodes the keystore from 4 GitHub Secrets, builds+signs, verifies the signing cert against BRIEF.md's recorded fingerprint before publishing (refuses to publish on a mismatch), creates the release tag server-side, publishes a GitHub Release with the APK attached. Filled in ship.sh's real gate (engine+data tests, versionCode-vs-BUILDLOG.md check, push, then hands off to the MCP-tool-driven trigger+confirm+record-release.sh sequence ckpt.sh can't do itself). Deleted the keystore material from the scratchpad after delivery. Local engine+data tests re-verified green after all build.gradle.kts changes (still 54/54).

## Do this next
BLOCKED on Tj: cannot trigger a working release build until he adds the 4 GitHub secrets (KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD) per the instructions sent with the keystore file. Once he confirms that's done: trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm green via mcp__github__get_release_by_tag (tag v0.1.0), run tools/record-release.sh v0.1.0 1 "first beta release", then send Tj the Release page link as plain tappable text, never in a code block.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  3fddc9e ckpt 126: Wrote Tj's 'where is the apk' request into TASKS.md. Confirmed this container'
  6f8d067 ckpt 123: CI confirmed fully green (run 35493330913): all tests across engine+data+app p
  49c1307 ckpt 96: Built the first real app code: 'Vigilant' — a 3-module Gradle project (engine
  3edb9b3 ckpt 29: Wrote Tj's 'begin basic coding' request into TASKS.md (raw message already capt
  c361581 ckpt 26: Deep-dived Odds Assist Pro against Novig, hands-on (not just secondhand): loade
  4ce2d00 ckpt 21: Wrote Tj's Odds Assist Pro deep-dive request into TASKS.md (raw message already
  5787e5b ckpt 18: Deep research on positive-EV betting for Novig, written to RESEARCH.md (new per
  1c3d806 ckpt 15: Wrote Tj's positive-EV research request into TASKS.md in his own words as untic
  e870ad3 ckpt 12: Stood up the full checkpoint/handoff system for novig, adapted from fantasy-foo
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
