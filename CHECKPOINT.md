# CHECKPOINT 2044 — read me first, then TASKS.md

**Written:** 2026-09-29T04:52:08Z · **tests:** all 2 fast checks green
**Branch:** `ccr-538c37db-finzj9` · **builds on:** `8086fda5` (this checkpoint is the commit after it)

## Just done
T2 built: BetRecheck reads every open bet (no cap), one Report that adds up to the open count, progress, batched saves, pause/failure stop, book snapshot on each bet; TrackedBet gets books/nowAmerican/gradeNote; stats audit fixes in BetTracker (expected vs actual on the same settled bets, voided, open money, luck)

## Do this next
Fix BetTrackerTest for new stats fields, add stats tests; then T4 grading (reasons, NHL/NBA/tennis parsers, PropLine fallback, regrade)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/src/main/kotlin/com/tjshea/vigilant/app/ui/TrackerScreen.kt

## Last ten checkpoints
```
  7e5855dc ckpt 2043: T1 done: tracker read end to end; root causes, PropLine grading research and
  f76a71d6 ckpt 2042: Recorded Tj's tracker request (T1-T9) in TASKS.md
  1d3ddf68 ckpt 2041: Z6 done: v0.19.7 (code 42) released and recorded (CI + release green); light
  7ad5dfc1 ckpt 2038: pre-release: v0.19.7: CNO's list leaves out futures and awards (games only; 
  9b9532e8 ckpt 2037: Z3-Z5 done: setup script verified + hardened (never fails, build-tools 35, -
  fc37a770 ckpt 2029: Z4/Z5 in progress: setup-android.sh never fails a session start (WARN + exit
  0f7eae8e ckpt 2015: Z1: Tj's decisions recorded (public keystore fine; no futures) in BRIEF/TASK
  914f38a5 ckpt 2011: Recorded Tj's request (keys public is fine; skills across accounts?; futures
  76da9a11 ckpt 2009: Y6 done: all checks green; PROMPT_AUDIT.md marked applied, patch removed; Y1
  8300accc ckpt 2007: Y3 + Y5 done: stale comments/labels fixed (engine:test 39/0), six stale TASK
```

(10 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
