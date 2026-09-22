# CHECKPOINT 319 — read me first, then TASKS.md

**Written:** 2026-09-22T05:47:36Z · **tests:** all 1 fast checks green
**Branch:** `claude/app-overhaul-novig-data-ut6a84` · **builds on:** `96360b7` (this checkpoint is the commit after it)

## Just done
Confirmed CI green for real on the direct-mode addition (run 35692029112, conclusion=success — engine+data+app all compiled, all tests passed, assembleDebug succeeded). Bumped versionCode 4->5 / versionName 0.3.0->0.3.1.

## Do this next
Trigger release.yml, confirm green, record in BUILDLOG.md, tell Tj v0.3.1 is ready — ships the free direct-access-no-proxy option he asked about.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  82ed486 ckpt 318: Updated BRIEF.md/RESEARCH.md (new §4.4.1) documenting the free direct-access 
  a94144e ckpt 317: Added a free 'direct access, no proxy' opt-in path answering Tj's question abo
  684b84d ckpt 316: Shipped v0.3.0 for real: confirmed CI green (run 35690359093) and release gree
  d18bc8a ckpt 315: Confirmed CI green for real on the full overhaul (run 35690359093, conclusion=
  9e2539c ckpt 314: Updated BRIEF.md (Locked architecture decisions — NovigGraphQlClient replace
  2e79d94 ckpt 313: Built and verified NovigGraphQlClient (data module): real, direct GraphQL clie
  1255f07 ckpt 312: Reviewed Tj's overhaul request + all 3 attachments (briefing PDF + actual novi
  78a67b4 ckpt 311: Answered Tj's question about whether anything else in SharpAPI's free tier is 
  ebb2cb7 ckpt 308: Researched and confirmed: there is no $0/mo path to real Novig odds data from 
  32c58c9 ckpt 298: v0.2.1's diagnostic fix worked: Tj's retry now shows 'Last failure: invalid (H
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
