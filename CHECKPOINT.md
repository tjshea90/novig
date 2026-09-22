# CHECKPOINT 315 — read me first, then TASKS.md

**Written:** 2026-09-22T05:22:36Z · **tests:** all 1 fast checks green
**Branch:** `claude/app-overhaul-novig-data-ut6a84` · **builds on:** `e8bfaed` (this checkpoint is the commit after it)

## Just done
Confirmed CI green for real on the full overhaul (run 35690359093, conclusion=success) — engine+data+app all compile, every unit test passes including the 22 new NovigGraphQlClient tests, assembleDebug produced a real APK. Bumped versionCode 3->4 / versionName 0.2.1->0.3.0 (BRIEF.md's rule — code 3 already shipped).

## Do this next
Trigger release.yml, confirm green, record in BUILDLOG.md, send Tj the v0.3.0 Release link plus an honest chat summary of the ToS/proxy-cost tradeoff and what he needs to do next (get a proxy provider, paste credentials into Settings).

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  9e2539c ckpt 314: Updated BRIEF.md (Locked architecture decisions — NovigGraphQlClient replace
  2e79d94 ckpt 313: Built and verified NovigGraphQlClient (data module): real, direct GraphQL clie
  1255f07 ckpt 312: Reviewed Tj's overhaul request + all 3 attachments (briefing PDF + actual novi
  78a67b4 ckpt 311: Answered Tj's question about whether anything else in SharpAPI's free tier is 
  ebb2cb7 ckpt 308: Researched and confirmed: there is no $0/mo path to real Novig odds data from 
  32c58c9 ckpt 298: v0.2.1's diagnostic fix worked: Tj's retry now shows 'Last failure: invalid (H
  8fe4bd2 ckpt 296: v0.2.1 shipped: contains the two fixes from the 'Scan failed' bug report (auto
  07d9c22 ckpt 288: Logged Tj's 'ship v0.2.1 now' request in TASKS.md, bumped versionCode 2->3 / v
  e074e62 ckpt 284: Confirmed CI green for real on the Scan-failed diagnosis/fix push (run 3553801
  68c4176 ckpt 278: Diagnosed and fixed Tj's 'Scan failed — rate-limited or invalid' report (v0.
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
