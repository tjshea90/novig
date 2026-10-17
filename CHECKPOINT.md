# CHECKPOINT 3588 — read me first, then TASKS.md

**Written:** 2026-10-17T19:58:18Z · **tests:** all 5 fast checks green
**Branch:** `claude/wizardly-meitner-h2g4pr` · **builds on:** `1b62190c6` (this checkpoint is the commit after it)

## Just done
pre-release: v0.114.28: an order Novig leaves pending is cancelled by its id and re-read before the engine stops ordering, so one slow answer no longer halts trading for 30 minutes (versionCode 324, v0.114.28)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.114.28), then run: bash tools/record-release.sh v0.114.28 324 "v0.114.28: an order Novig leaves pending is cancelled by its id and re-read before the engine stops ordering, so one slow answer no longer halts trading for 30 minutes"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  1b62190c6 ckpt 3587: pre-ship: v0.114.28: an order Novig leaves pending is cancelled by its id a
  89fcdca7e ckpt 3586: Engine: pending IOC is cancelled by id and re-read before the 'unconfirmed'
  e4bb873f6 ckpt 3585: v0.114.27 released; BoltOdds key on GitHub, switch request sent
  70572032a ckpt 3584: pre-release: v0.114.27: Live light logs every decision (scans with score an
  7cb98b00e ckpt 3583: pre-ship: v0.114.27: Live light logs every decision (scans with score and c
  e11340b99 ckpt 3582: TCL101a: API review; Unabated off; BoltOdds idle flagged to Tj
  da30810d2 ckpt 3581: TCL100a/b: Live light journal (scan/offer/bid) saved to phone-data phone/li
  8055f04e5 ckpt 3580: pre-release: v0.114.26: ParlayAPI and other monthly credits are paced fairl
  33fd7dc72 ckpt 3579: pre-ship: v0.114.26: ParlayAPI and other monthly credits are paced fairly f
  53cc017be ckpt 3578: TCL99a: credit pacing no longer locks out a key first met mid-month with a 
```
