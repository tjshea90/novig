# CHECKPOINT 4135 — read me first, then TASKS.md

**Written:** 2026-10-24T18:12:50Z · **tests:** all 9 fast checks green
**Branch:** `claude/optimistic-curie-hj14wp` · **builds on:** `905ac787a` (this checkpoint is the commit after it)

## Just done
pre-ship: CH80: the +EV list no longer empties at each engine scan start: rows the new scan has not re-read yet stay listed with their own stamps (found from the phone's FEEDHIDE log)

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/build.gradle.kts
     M research/review/CHANGES.md

## Last ten checkpoints
```
  89aa892f4 ckpt 4134: pre-release: After the Diagnostics report is saved to GitHub the phone clea
  f42da88fd ckpt 4133: pre-ship: After the Diagnostics report is saved to GitHub the phone clears 
  04c7f1a26 ckpt 4132: pre-release: The phone saves its full Diagnostics report to GitHub every 20
  45ac45990 ckpt 4131: pre-ship: The phone saves its full Diagnostics report to GitHub every 20 mi
  c27122c3f ckpt 4130: pre-release: CH82+CH81: engine restart reads 'stopped or restarting' not 's
  8589e8341 ckpt 4129: pre-ship: CH82+CH81: engine restart reads 'stopped or restarting' not 'swit
  88138eab6 ckpt 4128: CH80b candidate cause noted (requoteListed partial books)
  70fcf7828 ckpt 4127: CH83 scoped: archive not write-behind
  92ec17a42 ckpt 4126: CH83 written; diagnostics paste triaged
  6d84a658e ckpt 4125: v1.0.73 released+recorded; CH82 wording: stopped or restarting (tests green
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
