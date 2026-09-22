# CHECKPOINT 327 — read me first, then TASKS.md

**Written:** 2026-09-22T06:35:46Z · **tests:** all 1 fast checks green
**Branch:** `claude/app-overhaul-novig-data-ut6a84` · **builds on:** `5d1c5a0` (this checkpoint is the commit after it)

## Just done
Bumped versionCode 6->7 / versionName 0.3.2->0.3.3 for the OkHttp Authenticator fix.

## Do this next
Verify CI green for real, trigger release, confirm green, record BUILDLOG, tell Tj.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  cb8e8e5 ckpt 326: Diagnosed and fixed a real OkHttp Authenticator bug found from Tj's own device
  736058e ckpt 325: Shipped v0.3.2 for real (confirmed green: run 35693512531, release published).
  68871a7 ckpt 324: Extended the release.yml self-heal fix: the actual failure was a leftover DRAF
  982ee12 ckpt 323: Diagnosed and fixed a real release.yml bug hit for real shipping v0.3.2: cance
  1b42a2e ckpt 322: Bumped versionCode 5->6 / versionName 0.3.1->0.3.2 for the 503-classification 
  269d547 ckpt 321: Tj tried the new direct-mode toggle for real and hit a genuine HTTP 503 from N
  5b43606 ckpt 320: Shipped v0.3.1 for real: confirmed CI green (run 35692029112) and release gree
  668dbd5 ckpt 319: Confirmed CI green for real on the direct-mode addition (run 35692029112, conc
  82ed486 ckpt 318: Updated BRIEF.md/RESEARCH.md (new §4.4.1) documenting the free direct-access 
  a94144e ckpt 317: Added a free 'direct access, no proxy' opt-in path answering Tj's question abo
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
