# CHECKPOINT 2400 — read me first, then TASKS.md

**Written:** 2026-10-02T22:12:11Z · **tests:** all 3 fast checks green
**Branch:** `ccr-91942f39-sfdkh9` · **builds on:** `e1cf9424` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.50.0: every Vigilant notification (push or silent) shows the wallet's current balance in its header; when the wallet runs out, Vigilant pauses all scanning (asleep) and says so until you add money and resume

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  b219e900 ckpt 2399: pre-release: v0.49.1: every Vigilant notification (push or silent) shows the
  51c28710 ckpt 2398: pre-ship: v0.49.1: every Vigilant notification (push or silent) shows the Vi
  da773428 ckpt 2397: BB released next (v0.49.0 gated); BC: Tj's wallet-balance-in-every-notificat
  03dd8ef6 ckpt 2396: pre-release: v0.49.0: Novig only compares Novig's odds now with the odds you
  80a2626d ckpt 2395: pre-ship: v0.49.0: Novig only compares Novig's odds now with the odds you be
  7f11ca5d ckpt 2394: BB1+BB2 done: Novig only compares Novig's odds now with the odds bet at (off
  73eb3b3f ckpt 2393: BB: Tj's Novig-only request (Novig's current odds are the fair; no other boo
  075cc8f1 ckpt 2392: pre-release: v0.48.1: full-test fixes: locks out of every CLV stat and the c
  cecc64c5 ckpt 2391: pre-ship: v0.48.1: full-test fixes: locks out of every CLV stat and the chec
  a1a166e8 ckpt 2390: BA6 fixes F1-F9 in, each with a failing-first test (StatsAccuracyTest F1-F4/
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
