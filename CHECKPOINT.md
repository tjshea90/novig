# CHECKPOINT 3385 — read me first, then TASKS.md

**Written:** 2026-10-15T23:36:18Z · **tests:** all 5 fast checks green
**Branch:** `ccr-09f8e1bf-qtmu63` · **builds on:** `8464e7d5f` (this checkpoint is the commit after it)

## Just done
TCL29 chat built (model, phone page, watcher, tell_tj chat-reply), away.json + AWAY rotation state for acct-7542bf until Fri 22:00 ET

## Do this next
TCL29d: bump app versionCode 291/0.111.0, bash tools/test.sh, bash ship.sh, release.yml, record-release, tell Tj; other sessions: start chat_watch.sh, cover AWAY slot

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M CLAUDE.md
     M TASKS.md
     M tools/resume.sh

## Last ten checkpoints
```
  dfe553bef ckpt 3384: slot3 ENGINEER: WATCH only, engine healthy, 0 orders held by trap guard
  04f6651f0 ckpt 3383: TCL28 shipped as v0.110.0 (released, recorded), engine restarted on new cod
  e21ac688f ckpt 3382: pre-release: v0.110.0: the engine rests/turns off silent APIs and advises w
  4c3771fc8 ckpt 3381: pre-ship: v0.110.0: the engine rests/turns off silent APIs and advises what
  7248cc027 ckpt 3380: TCL28b/a: governor + advisor wired into the engine (rests silent APIs, asks
  cd1d34a78 ckpt 3379: TCL28 pure parts: ApiToggles, CloudMessages, CloudOverrides, ApiAdvisor, Ap
  56e145350 ckpt 3377: pre-release: v0.109.1: API keys added or changed in the phone app are uploa
  7c2be47df ckpt 3376: pre-ship: v0.109.1: API keys added or changed in the phone app are uploaded
  fbdc0bfbb ckpt 3375: edge floor identified as the volume limiter; rule for lowering it written (
  e5fbd18d4 ckpt 3374: filters judged on the digest: trap guard keep, others no proof; Tj's full-r
```

(10 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
