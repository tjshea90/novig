# CHECKPOINT 316 — read me first, then TASKS.md

**Written:** 2026-09-22T05:25:22Z · **tests:** all 1 fast checks green
**Branch:** `claude/app-overhaul-novig-data-ut6a84` · **builds on:** `a937391` (this checkpoint is the commit after it)

## Just done
Shipped v0.3.0 for real: confirmed CI green (run 35690359093) and release green (run 35690476182, signed APK 21.5MB, signature verified against BRIEF.md's recorded fingerprint, GitHub Release published), recorded in BUILDLOG.md. This closes out Tj's 'overhaul the app to use the novig data from the method attached' request end to end: NovigGraphQlClient (real, verified, unauthenticated direct-to-Novig-backend access), fee fix, EventMatcher order-independence fix, dead code removed, Settings UI wired with prominent risk disclosure, 95 tests green, docs updated.

## Do this next
Report back to Tj: send the v0.3.0 Release link as plain tappable text, plus an honest summary of what changed and the real risk/cost tradeoff (proxies required, ToS gray area, no account risk since it's unauthenticated). Nothing further queued unless Tj asks for something new.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  d18bc8a ckpt 315: Confirmed CI green for real on the full overhaul (run 35690359093, conclusion=
  9e2539c ckpt 314: Updated BRIEF.md (Locked architecture decisions — NovigGraphQlClient replace
  2e79d94 ckpt 313: Built and verified NovigGraphQlClient (data module): real, direct GraphQL clie
  1255f07 ckpt 312: Reviewed Tj's overhaul request + all 3 attachments (briefing PDF + actual novi
  78a67b4 ckpt 311: Answered Tj's question about whether anything else in SharpAPI's free tier is 
  ebb2cb7 ckpt 308: Researched and confirmed: there is no $0/mo path to real Novig odds data from 
  32c58c9 ckpt 298: v0.2.1's diagnostic fix worked: Tj's retry now shows 'Last failure: invalid (H
  8fe4bd2 ckpt 296: v0.2.1 shipped: contains the two fixes from the 'Scan failed' bug report (auto
  07d9c22 ckpt 288: Logged Tj's 'ship v0.2.1 now' request in TASKS.md, bumped versionCode 2->3 / v
  e074e62 ckpt 284: Confirmed CI green for real on the Scan-failed diagnosis/fix push (run 3553801
```

(10 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
