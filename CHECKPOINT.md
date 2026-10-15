# CHECKPOINT 3377 — read me first, then TASKS.md

**Written:** 2026-10-15T21:55:47Z · **tests:** all 5 fast checks green
**Branch:** `ccr-09f8e1bf-qtmu63` · **builds on:** `d90385a15` (this checkpoint is the commit after it)

## Just done
pre-release: v0.109.1: API keys added or changed in the phone app are uploaded to GitHub secrets by themselves (while the cloud scan is on), so the engine always has them (versionCode 289, v0.109.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.109.1), then run: bash tools/record-release.sh v0.109.1 289 "v0.109.1: API keys added or changed in the phone app are uploaded to GitHub secrets by themselves (while the cloud scan is on), so the engine always has them"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M data/src/main/kotlin/com/tjshea/vigilant/data/cloud/ApiToggles.kt
     M data/src/main/kotlin/com/tjshea/vigilant/data/cloud/CloudMessages.kt
    ?? data/src/main/kotlin/com/tjshea/vigilant/data/cloud/ApiAdvisor.kt
    ?? data/src/test/kotlin/com/tjshea/vigilant/data/cloud/ApiAdvisorTest.kt

## Last ten checkpoints
```
  7c2be47df ckpt 3376: pre-ship: v0.109.1: API keys added or changed in the phone app are uploaded
  fbdc0bfbb ckpt 3375: edge floor identified as the volume limiter; rule for lowering it written (
  e5fbd18d4 ckpt 3374: filters judged on the digest: trap guard keep, others no proof; Tj's full-r
  6a29ad8e9 ckpt 3373: TCL26: engine re-reads listed bets before judging (fixes stale-price refusa
  474eb2e6d ckpt 3372: test_coord: board window 8 (a same-second paused note pushed the checked no
  c1d020fb9 ckpt 3371: TCL25: paused routines resume by themselves (rotation-paused note, wait-fre
  d6ff31d36 ckpt 3370: merged main (routine's TCL21f silent-feed detector) with the chat branch: r
  992347603 ckpt 3369: ENGINEER 2026-10-15 done, lease released
  f1835250d ckpt 3368: TCL21f engine-side silent-feed detector (FeedWatch, status.feedAlerts, dash
  fc4704199 ckpt 3367: one-account behaviour: Tj's message in any account takes the lease from a r
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
