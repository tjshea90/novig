# CHECKPOINT 324 — read me first, then TASKS.md

**Written:** 2026-09-22T06:05:36Z · **tests:** all 1 fast checks green
**Branch:** `claude/app-overhaul-novig-data-ut6a84` · **builds on:** `d7f7247` (this checkpoint is the commit after it)

## Just done
Extended the release.yml self-heal fix: the actual failure was a leftover DRAFT release (softprops/action-gh-release got cancelled mid-creation, leaving a draft with tag_name='v0.3.2' but attached to a synthetic untagged-... ref instead of the real tag) — gh release view matches by the tag_name field, so it found this draft and my first fix wrongly treated it as a real published release. Now deletes a leftover draft first (this project never intentionally creates drafts, so any draft matching the tag name is always safe cruft), then refuses only on a genuinely published release, then cleans up a bare stale tag same as before.

## Do this next
Push, re-trigger release.yml for v0.3.2, confirm every step green this time, record BUILDLOG, tell Tj.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  982ee12 ckpt 323: Diagnosed and fixed a real release.yml bug hit for real shipping v0.3.2: cance
  1b42a2e ckpt 322: Bumped versionCode 5->6 / versionName 0.3.1->0.3.2 for the 503-classification 
  269d547 ckpt 321: Tj tried the new direct-mode toggle for real and hit a genuine HTTP 503 from N
  5b43606 ckpt 320: Shipped v0.3.1 for real: confirmed CI green (run 35692029112) and release gree
  668dbd5 ckpt 319: Confirmed CI green for real on the direct-mode addition (run 35692029112, conc
  82ed486 ckpt 318: Updated BRIEF.md/RESEARCH.md (new §4.4.1) documenting the free direct-access 
  a94144e ckpt 317: Added a free 'direct access, no proxy' opt-in path answering Tj's question abo
  684b84d ckpt 316: Shipped v0.3.0 for real: confirmed CI green (run 35690359093) and release gree
  d18bc8a ckpt 315: Confirmed CI green for real on the full overhaul (run 35690359093, conclusion=
  9e2539c ckpt 314: Updated BRIEF.md (Locked architecture decisions — NovigGraphQlClient replace
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
