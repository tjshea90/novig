# CHECKPOINT 3763 — read me first, then TASKS.md

**Written:** 2026-10-19T19:44:01Z · **tests:** all 6 fast checks green
**Branch:** `ccr-413cf302-mvy06f` · **builds on:** `d0265c14a` (this checkpoint is the commit after it)

## Just done
pre-release: v1.0.7: the engine waits 20 s for your settings edits to settle before restarting a scan (SC1: three edits in 6 min cut three scans short); cleanup of old releases/branches (versionCode 347, v1.0.7)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.7), then run: bash tools/record-release.sh v1.0.7 347 "v1.0.7: the engine waits 20 s for your settings edits to settle before restarting a scan (SC1: three edits in 6 min cut three scans short); cleanup of old releases/branches"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6e1d35722 ckpt 3762: pre-ship: v1.0.7: the engine waits 20 s for your settings edits to settle b
  9eada62c3 ckpt 3761: handoff to mirage written (TASKS.md HANDOFF TO MIRAGE + board note); CH24b 
  ea8cd737f ckpt 3760: added delete-old-releases.yml (APK releases only, keep newest 3 + v0.17.0, 
  74f7f5c20 ckpt 3759: added delete-stale-branches.yml (manual one-shot, 71 listed session branche
  a1a0f2073 ckpt 3758: merged mirage's CH24a (watchdog unreadable-branch fix) into my branch, self
  65d1d1f29 ckpt 3757: ENGINEER 10-19 routine: WATCH only, engine healthy, no change
  0e0b99791 ckpt 3756: v1.0.6 released (PL + PL5) and recorded; chat told; keeper run 39284378407 
  c34750c5c ckpt 3755: pre-release: v1.0.6: the Tracker's profit card rebuilt (PL): Today is today
  346639a1e ckpt 3754: EA8a/EA9 written up (keeper import crash, CI flood: ci.yml main-only, data-
  8e2363d3d ckpt 3753: PL: Tracker tests follow the new card (all-time headline by tag, clv card s
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
