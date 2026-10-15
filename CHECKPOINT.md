# CHECKPOINT 3320 — read me first, then TASKS.md

**Written:** 2026-10-15T02:59:23Z · **tests:** all 4 fast checks green
**Branch:** `ccr-4dcbaa5f-skgw6q` · **builds on:** `b58fd2248` (this checkpoint is the commit after it)

## Just done
TCL16b: Feed freshness chip splits games within 12 h from the far ones (CloudFreshness + test); v0.106.0 release.yml and new engine run 38673681637 in flight

## Do this next
Confirm v0.106.0 release landed (get_release_by_tag), record-release.sh v0.106.0 284 ...; read st.sh for the new engine's first scan (hot refresh counts in scanNote, fair age median for rows within 12h vs before 14.7 min); write answer to Tj; TCL16c decisions

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  d5cef8451 ckpt 3319: pre-release: v0.106.0: the GitHub engine keeps games about to start young w
  870e6c72b ckpt 3318: pre-ship: v0.106.0: the GitHub engine keeps games about to start young whil
  26f23ed9d ckpt 3317: TCL16a: hot refresh in the scanner (fair feeds + likeliest Novig books of g
  03246fa10 ckpt 3316: v0.105.0 recorded; engine scan note carries timing; TCL16 written from live
  079c76aa5 ckpt 3315: pre-release: v0.105.0: API usage meters stay accurate with the scan on GitH
  14168bece ckpt 3314: pre-ship: v0.105.0: API usage meters stay accurate with the scan on GitHub:
  e1c031802 ckpt 3313: TCL14: usage meters merged (engine usage.json by key id, seed across handov
  da6b9cf15 ckpt 3312: TCL14 written (usage meters accurate with scans on GitHub); v0.104.0 releas
  926e29cfc ckpt 3311: pre-release: v0.104.0: Novig and BoltOdds keys can go to the GitHub engine 
  a50f88791 ckpt 3310: pre-ship: v0.104.0: Novig and BoltOdds keys can go to the GitHub engine (a 
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
