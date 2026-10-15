# CHECKPOINT 3380 — read me first, then TASKS.md

**Written:** 2026-10-15T22:00:16Z · **tests:** all 5 fast checks green
**Branch:** `ccr-09f8e1bf-qtmu63` · **builds on:** `387a4d284` (this checkpoint is the commit after it)

## Just done
TCL28b/a: governor + advisor wired into the engine (rests silent APIs, asks the phone to turn a dead one off, advice in status+dashboard, messages for bets placed / stops / advice), tests

## Do this next
CloudClient: list messages/overrides dirs; phone loops (apply overrides once, store messages), Messages screen, tell_tj tool, docs, release

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M data/src/main/kotlin/com/tjshea/vigilant/data/cloud/CloudDashboard.kt
     M data/src/test/kotlin/com/tjshea/vigilant/data/cloud/CloudDashboardTest.kt

## Last ten checkpoints
```
  cd1d34a78 ckpt 3379: TCL28 pure parts: ApiToggles, CloudMessages, CloudOverrides, ApiAdvisor, Ap
  56e145350 ckpt 3377: pre-release: v0.109.1: API keys added or changed in the phone app are uploa
  7c2be47df ckpt 3376: pre-ship: v0.109.1: API keys added or changed in the phone app are uploaded
  fbdc0bfbb ckpt 3375: edge floor identified as the volume limiter; rule for lowering it written (
  e5fbd18d4 ckpt 3374: filters judged on the digest: trap guard keep, others no proof; Tj's full-r
  6a29ad8e9 ckpt 3373: TCL26: engine re-reads listed bets before judging (fixes stale-price refusa
  474eb2e6d ckpt 3372: test_coord: board window 8 (a same-second paused note pushed the checked no
  c1d020fb9 ckpt 3371: TCL25: paused routines resume by themselves (rotation-paused note, wait-fre
  d6ff31d36 ckpt 3370: merged main (routine's TCL21f silent-feed detector) with the chat branch: r
  992347603 ckpt 3369: ENGINEER 2026-10-15 done, lease released
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
