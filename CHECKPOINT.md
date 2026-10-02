# CHECKPOINT 2396 — read me first, then TASKS.md

**Written:** 2026-10-02T21:51:10Z · **tests:** all 3 fast checks green
**Branch:** `ccr-91942f39-sfdkh9` · **builds on:** `80a2626d` (this checkpoint is the commit after it)

## Just done
pre-release: v0.49.0: Novig only compares Novig's odds now with the odds you bet at (same odds = 0% EV; no other book's data anywhere on it); full-test fixes: locks out of CLV and the check counter, locked markets graded from score feeds, closes dated by their oldest price, live bets' fair/Kelly/push at cost with the fee, 'profit if all win' (versionCode 88, v0.49.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.49.0), then run: bash tools/record-release.sh v0.49.0 88 "v0.49.0: Novig only compares Novig's odds now with the odds you bet at (same odds = 0% EV; no other book's data anywhere on it); full-test fixes: locks out of CLV and the check counter, locked markets graded from score feeds, closes dated by their oldest price, live bets' fair/Kelly/push at cost with the fee, 'profit if all win'"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  80a2626d ckpt 2395: pre-ship: v0.49.0: Novig only compares Novig's odds now with the odds you be
  7f11ca5d ckpt 2394: BB1+BB2 done: Novig only compares Novig's odds now with the odds bet at (off
  73eb3b3f ckpt 2393: BB: Tj's Novig-only request (Novig's current odds are the fair; no other boo
  075cc8f1 ckpt 2392: pre-release: v0.48.1: full-test fixes: locks out of every CLV stat and the c
  cecc64c5 ckpt 2391: pre-ship: v0.48.1: full-test fixes: locks out of every CLV stat and the chec
  a1a166e8 ckpt 2390: BA6 fixes F1-F9 in, each with a failing-first test (StatsAccuracyTest F1-F4/
  ebabf7e1 ckpt 2389: BA1-BA4 audited: floor green + screenshots; fixes found F1-F9 (TASKS.md BA2-
  d7501949 ckpt 2388: BA: Tj's full-tests request (math, stats, closing lines) written into TASKS.
  b9f36b48 ckpt 2387: AZ done: v0.48.0 released, verified, recorded (hide locked bets + Locked in 
  511ed2bf ckpt 2386: pre-release: v0.48.0: locked markets hidden from the Tracker's lists and sta
```
