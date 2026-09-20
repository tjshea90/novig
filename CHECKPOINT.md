# CHECKPOINT 12 — read me first, then TASKS.md

**Written:** 2026-09-20T04:12:45Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-checkpoint-tests-qqgnnb` · **builds on:** `d3aaeef` (this checkpoint is the commit after it)

## Just done
Stood up the full checkpoint/handoff system for novig, adapted from fantasy-football and Portfolio (both read-only references, never modified): tools/{secretscan,push,unpushed,autosave,capture_inbox,ckpt,resume,toobig,install-hooks,record-release}.sh, tools/session-root-hooks.json, tools/test_resume.sh (21 hermetic checks, all green), .claude/settings.json, bootstrap.sh, BRIEF.md, CLAUDE.md, TASKS.md, INBOX.md, BUILDLOG.md, ship.sh, .gitignore. Also copied and adapted Portfolio's 'light tests'/'full tests' protocols into CLAUDE.md, written generically since no app code exists yet. IMPORTANT DEVIATION FROM A LITERAL PORT: Portfolio's multi-repo hook design has each hook glob every sibling repo under the session root and run ITS OWN autosave.sh (git add/commit/push) in turn. That would mean this repo's infrastructure executing git commits/pushes inside fantasy-football's and Portfolio's real checkouts, which the task explicitly requires stay read-only. Redesigned novig's hooks to be absolute paths into this repo's own tools/ only, verified empirically (installed for real at the session root, confirmed via git status/log that both sibling repos remain completely untouched throughout, and added a dedicated test section proving no installed hook ever contains a sibling path or a glob). All 21 tools/test_resume.sh checks pass. The real session-root hooks are installed and confirmed firing (11 auto-checkpoint commits landed and pushed to GitHub, including a main fast-forward, during this session's own work).

## Do this next
The checkpoint/test-protocol infrastructure this request asked for is complete and verified working end-to-end. Nothing about the actual novig app exists yet on purpose (see BRIEF.md's TBD sections and TASKS.md's 'Not yet started' pointer) — the next real task is Tj deciding/describing the app's actual architecture and Novig-sportsbook profit strategy, which should get written into TASKS.md in his own words before any app code is written, per this same CLAUDE.md's own rule.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
    ?? CHECKPOINT.md

## Last ten checkpoints
```
```
