# CHECKPOINT 3587 — read me first, then TASKS.md

**Written:** 2026-10-17T19:51:56Z · **tests:** all 5 fast checks green
**Branch:** `claude/wizardly-meitner-h2g4pr` · **builds on:** `793a1814c` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.114.28: an order Novig leaves pending is cancelled by its id and re-read before the engine stops ordering, so one slow answer no longer halts trading for 30 minutes

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  89fcdca7e ckpt 3586: Engine: pending IOC is cancelled by id and re-read before the 'unconfirmed'
  e4bb873f6 ckpt 3585: v0.114.27 released; BoltOdds key on GitHub, switch request sent
  70572032a ckpt 3584: pre-release: v0.114.27: Live light logs every decision (scans with score an
  7cb98b00e ckpt 3583: pre-ship: v0.114.27: Live light logs every decision (scans with score and c
  e11340b99 ckpt 3582: TCL101a: API review; Unabated off; BoltOdds idle flagged to Tj
  da30810d2 ckpt 3581: TCL100a/b: Live light journal (scan/offer/bid) saved to phone-data phone/li
  8055f04e5 ckpt 3580: pre-release: v0.114.26: ParlayAPI and other monthly credits are paced fairl
  33fd7dc72 ckpt 3579: pre-ship: v0.114.26: ParlayAPI and other monthly credits are paced fairly f
  53cc017be ckpt 3578: TCL99a: credit pacing no longer locks out a key first met mid-month with a 
  dbe32328a ckpt 3577: v0.114.25 released: PropLine +EV list retired; engine restarted with the sw
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
