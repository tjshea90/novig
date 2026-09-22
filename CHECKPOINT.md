# CHECKPOINT 323 — read me first, then TASKS.md

**Written:** 2026-09-22T06:02:30Z · **tests:** all 1 fast checks green
**Branch:** `claude/app-overhaul-novig-data-ut6a84` · **builds on:** `db8f3b2` (this checkpoint is the commit after it)

## Just done
Diagnosed and fixed a real release.yml bug hit for real shipping v0.3.2: cancelling a stuck release run doesn't stop an in-flight step instantly, so its tag-push step raced ahead and completed (leaving a real tag with no GitHub Release attached) before the release-creation step got cut off. The retry's own 'refuse to overwrite' safety check then let the same tag collide again, because it checked the local shallow checkout (unreliable for a tag pushed to remote moments earlier) instead of the remote directly. Fixed: the check now queries the remote and self-heals the one provably-safe case (a bare tag with no Release) while still refusing anything with a real Release. Recorded as a 7th build trap in BRIEF.md.

## Do this next
Push, re-trigger release.yml for v0.3.2, confirm it goes green for real this time, record BUILDLOG, tell Tj the 503-classification fix is shipped plus the honest read on what his 503 probably means.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  1b42a2e ckpt 322: Bumped versionCode 5->6 / versionName 0.3.1->0.3.2 for the 503-classification 
  269d547 ckpt 321: Tj tried the new direct-mode toggle for real and hit a genuine HTTP 503 from N
  5b43606 ckpt 320: Shipped v0.3.1 for real: confirmed CI green (run 35692029112) and release gree
  668dbd5 ckpt 319: Confirmed CI green for real on the direct-mode addition (run 35692029112, conc
  82ed486 ckpt 318: Updated BRIEF.md/RESEARCH.md (new §4.4.1) documenting the free direct-access 
  a94144e ckpt 317: Added a free 'direct access, no proxy' opt-in path answering Tj's question abo
  684b84d ckpt 316: Shipped v0.3.0 for real: confirmed CI green (run 35690359093) and release gree
  d18bc8a ckpt 315: Confirmed CI green for real on the full overhaul (run 35690359093, conclusion=
  9e2539c ckpt 314: Updated BRIEF.md (Locked architecture decisions — NovigGraphQlClient replace
  2e79d94 ckpt 313: Built and verified NovigGraphQlClient (data module): real, direct GraphQL clie
```

(15 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
