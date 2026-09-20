# CHECKPOINT 256 — read me first, then TASKS.md

**Written:** 2026-09-20T20:50:53Z · **tests:** all 1 fast checks green
**Branch:** `claude/positive-ev-sports-research-n6jum5` · **builds on:** `65a1816` (this checkpoint is the commit after it)

## Just done
Confirmed CI green for real on the sport-picker + fix push (run 35536752615, conclusion=success). Updated TASKS.md with the confirmed run link, and recorded the stray-weight-import trap as BRIEF.md's 6th standing build trap so a future session doesn't rediscover it.

## Do this next
Trigger release.yml via workflow_dispatch (versionCode 2 / v0.2.0 already bumped in app/build.gradle.kts), confirm it goes green, record it in BUILDLOG.md via tools/record-release.sh, and send Tj the new Release link as plain tappable text.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  d4ea339 ckpt 249: Fixed a real CI failure caught on the first push of the sport picker (run 3553
  2c1f31c ckpt 241: Built the sport-selection picker per Tj's explicit instruction: no odds load f
  92edb91 ckpt 229: Wrote Tj's new sport-picker request into TASKS.md before writing any code, per
  1fe3119 ckpt 226: Confirmed CI green for the app module (run 35535815614, conclusion=success) �
  8be34bf ckpt 220: Wired Settings navigation into MainActivity (state-based, no nav library): Set
  aadab85 ckpt 208: Rewired TheOddsApiClient onto KeyRotator (was a single apiKey string) — auto
  8e70e49 ckpt 205: Built KeyRotator (data module) — provider-agnostic multi-key rotation with r
  b663452 ckpt 198: Wrote Tj's 'make the app functional' request into TASKS.md with the architectu
  54208cf ckpt 195: Updated RESEARCH.md §4.1.1 with the real API-access process Tj found (email d
  b3b36e3 ckpt 191: Wrote Tj's request into TASKS.md: he found the real API-access process (email 
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
