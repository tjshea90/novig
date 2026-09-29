# CHECKPOINT 2046 — read me first, then TASKS.md

**Written:** 2026-09-29T05:04:36Z · **tests:** all 2 fast checks green
**Branch:** `ccr-538c37db-finzj9` · **builds on:** `487939a3` (this checkpoint is the commit after it)

## Just done
T4/T3/T5/T6/T7 data layer done and tested: BetGrader (tennis, alternate totals, all Novig prop stats, Grade result/waiting/manual + evidence, whyNot), BetSettler notes/force/batched saves, BetInsight, TrackerBreakdown, BetReplace, BetRecheck.checkOne

## Do this next
UI: ViewModel (grade now, regrade, price, re-read, replace outcome) then TrackerScreen redesign (bet sheet, Replace button, awaiting-result notes, stats cards incl expected-vs-actual + breakdown) + MainActivity replace launch; then T8 notification actions

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  bca5fd2a ckpt 2045: T4 in progress: FreeScores reads ESPN hockey box (goals/assists/points/SOG/b
  7f48f897 ckpt 2044: T2 built: BetRecheck reads every open bet (no cap), one Report that adds up 
  7e5855dc ckpt 2043: T1 done: tracker read end to end; root causes, PropLine grading research and
  f76a71d6 ckpt 2042: Recorded Tj's tracker request (T1-T9) in TASKS.md
  1d3ddf68 ckpt 2041: Z6 done: v0.19.7 (code 42) released and recorded (CI + release green); light
  7ad5dfc1 ckpt 2038: pre-release: v0.19.7: CNO's list leaves out futures and awards (games only; 
  9b9532e8 ckpt 2037: Z3-Z5 done: setup script verified + hardened (never fails, build-tools 35, -
  fc37a770 ckpt 2029: Z4/Z5 in progress: setup-android.sh never fails a session start (WARN + exit
  0f7eae8e ckpt 2015: Z1: Tj's decisions recorded (public keystore fine; no futures) in BRIEF/TASK
  914f38a5 ckpt 2011: Recorded Tj's request (keys public is fine; skills across accounts?; futures
```

(11 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
