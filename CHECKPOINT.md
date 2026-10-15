# CHECKPOINT 3373 — read me first, then TASKS.md

**Written:** 2026-10-15T21:33:19Z · **tests:** all 5 fast checks green
**Branch:** `ccr-09f8e1bf-qtmu63` · **builds on:** `e7732bc66` (this checkpoint is the commit after it)

## Just done
TCL26: engine re-reads listed bets before judging (fixes stale-price refusals), silent skips explained, tradepass volume journal, standing permission + profit/volume goal in CLAUDE.md

## Do this next
restart the engine on the new code, WATCH first live fills; phone Feed-bar line for status.feedAlerts

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M CLAUDE.md
     M TASKS.md
     M data/src/test/kotlin/com/tjshea/vigilant/data/cloud/CloudTraderTest.kt

## Last ten checkpoints
```
  474eb2e6d ckpt 3372: test_coord: board window 8 (a same-second paused note pushed the checked no
  c1d020fb9 ckpt 3371: TCL25: paused routines resume by themselves (rotation-paused note, wait-fre
  d6ff31d36 ckpt 3370: merged main (routine's TCL21f silent-feed detector) with the chat branch: r
  992347603 ckpt 3369: ENGINEER 2026-10-15 done, lease released
  f1835250d ckpt 3368: TCL21f engine-side silent-feed detector (FeedWatch, status.feedAlerts, dash
  fc4704199 ckpt 3367: one-account behaviour: Tj's message in any account takes the lease from a r
  b80a0a4b5 ckpt 3367: WATCH ok: engine live-mode, scanning, 0 orders (passed 0), route key ok
  063b8f882 ckpt 3366: lease released; routine on account 2 works (stood down correctly)
  9c430c30c ckpt 3365: routine failure #2 recorded (classifier blocks scripts from a self-cloned r
  babb89b26 ckpt 3364: pre-release: v0.109.0: Engine dashboard inside the app (Settings > Engine d
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
