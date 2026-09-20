# CHECKPOINT 298 — read me first, then TASKS.md

**Written:** 2026-09-20T21:42:29Z · **tests:** all 1 fast checks green
**Branch:** `claude/positive-ev-sports-research-n6jum5` · **builds on:** `c0e2953` (this checkpoint is the commit after it)

## Just done
v0.2.1's diagnostic fix worked: Tj's retry now shows 'Last failure: invalid (HTTP 403)' instead of the old generic message — this settles that it's NOT the 12 req/min rate limit (that would be 429). It's a 403, meaning the key authenticates but SharpAPI is rejecting this specific request. Logged two live hypotheses in TASKS.md: (a) the free tier's book coverage may not actually extend to Novig despite being listed (RESEARCH.md §4.2.1 already flagged this as unconfirmed), or (b) SharpApiClient is missing a parameter the Playground sends automatically (sport/league, visible in its UI but not in our request).

## Do this next
Ask Tj to test the SharpAPI Playground with Sportsbook explicitly set to Novig (his screenshots were on DraftKings) and report whether it also 403s — that single test tells us whether this is an account/tier limitation or a fixable request-shape bug, before guessing further.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  8fe4bd2 ckpt 296: v0.2.1 shipped: contains the two fixes from the 'Scan failed' bug report (auto
  07d9c22 ckpt 288: Logged Tj's 'ship v0.2.1 now' request in TASKS.md, bumped versionCode 2->3 / v
  e074e62 ckpt 284: Confirmed CI green for real on the Scan-failed diagnosis/fix push (run 3553801
  68c4176 ckpt 278: Diagnosed and fixed Tj's 'Scan failed — rate-limited or invalid' report (v0.
  5417b81 ckpt 266: v0.2.0 shipped: sport-selection picker request is fully done end to end — da
  9c59997 ckpt 256: Confirmed CI green for real on the sport-picker + fix push (run 35536752615, c
  d4ea339 ckpt 249: Fixed a real CI failure caught on the first push of the sport picker (run 3553
  2c1f31c ckpt 241: Built the sport-selection picker per Tj's explicit instruction: no odds load f
  92edb91 ckpt 229: Wrote Tj's new sport-picker request into TASKS.md before writing any code, per
  1fe3119 ckpt 226: Confirmed CI green for the app module (run 35535815614, conclusion=success) �
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
