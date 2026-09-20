# CHECKPOINT 296 — read me first, then TASKS.md

**Written:** 2026-09-20T21:25:50Z · **tests:** all 1 fast checks green
**Branch:** `claude/positive-ev-sports-research-n6jum5` · **builds on:** `906a563` (this checkpoint is the commit after it)

## Just done
v0.2.1 shipped: contains the two fixes from the 'Scan failed' bug report (autocorrect disabled on the API-key input field, and the exhaustion error now names the actual HTTP status instead of a generic message). Confirmed via GitHub Release + BUILDLOG.md, TASKS.md fully ticked.

## Do this next
Nothing further queued unless Tj asks for something new or the improved error message on v0.2.1 surfaces a definitive HTTP 429 vs 401 next time he hits a scan failure.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  07d9c22 ckpt 288: Logged Tj's 'ship v0.2.1 now' request in TASKS.md, bumped versionCode 2->3 / v
  e074e62 ckpt 284: Confirmed CI green for real on the Scan-failed diagnosis/fix push (run 3553801
  68c4176 ckpt 278: Diagnosed and fixed Tj's 'Scan failed — rate-limited or invalid' report (v0.
  5417b81 ckpt 266: v0.2.0 shipped: sport-selection picker request is fully done end to end — da
  9c59997 ckpt 256: Confirmed CI green for real on the sport-picker + fix push (run 35536752615, c
  d4ea339 ckpt 249: Fixed a real CI failure caught on the first push of the sport picker (run 3553
  2c1f31c ckpt 241: Built the sport-selection picker per Tj's explicit instruction: no odds load f
  92edb91 ckpt 229: Wrote Tj's new sport-picker request into TASKS.md before writing any code, per
  1fe3119 ckpt 226: Confirmed CI green for the app module (run 35535815614, conclusion=success) �
  8be34bf ckpt 220: Wired Settings navigation into MainActivity (state-based, no nav library): Set
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
