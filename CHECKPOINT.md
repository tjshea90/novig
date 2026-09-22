# CHECKPOINT 328 — read me first, then TASKS.md

**Written:** 2026-09-22T06:49:19Z · **tests:** all 1 fast checks green
**Branch:** `claude/new-session-82rv4s` · **builds on:** `31feeb9` (this checkpoint is the commit after it)

## Just done
v0.3.3 shipped and confirmed green (CI run 35695873352, release run 35695667948, BUILDLOG recorded). Diagnosed Tj's new screenshot (HTTP 402 on proxy CONNECT): the v0.3.3 authenticator fix is confirmed working (clean single-shot error, no more tunnel-attempt-limit crash); the new 402 is the proxy provider itself demanding payment during tunnel setup, before the request ever reaches Novig -- an external trial/billing issue, not an app bug. No code change needed.

## Do this next
Wait on Tj: either he resolves the proxy trial/billing (pay or get a working credential) or switches to the free direct-mode toggle (v0.3.1, accepting its own already-disclosed rate-limit/block tradeoff). Nothing else queued.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  30a5637 ckpt 327: Bumped versionCode 6->7 / versionName 0.3.2->0.3.3 for the OkHttp Authenticato
  cb8e8e5 ckpt 326: Diagnosed and fixed a real OkHttp Authenticator bug found from Tj's own device
  736058e ckpt 325: Shipped v0.3.2 for real (confirmed green: run 35693512531, release published).
  68871a7 ckpt 324: Extended the release.yml self-heal fix: the actual failure was a leftover DRAF
  982ee12 ckpt 323: Diagnosed and fixed a real release.yml bug hit for real shipping v0.3.2: cance
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
