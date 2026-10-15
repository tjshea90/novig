# CHECKPOINT 3317 — read me first, then TASKS.md

**Written:** 2026-10-15T02:51:34Z · **tests:** all 4 fast checks green
**Branch:** `ccr-4dcbaa5f-skgw6q` · **builds on:** `051a83e2f` (this checkpoint is the commit after it)

## Just done
TCL16a: hot refresh in the scanner (fair feeds + likeliest Novig books of games within 12 h re-read during the long scan; engine only), HotRefreshTest, scan note carries ScanTiming

## Do this next
TCL16: write research/scan_speed_2026-10-15.md (numbers: 9,619 books/537 s at 16/s; fair 14.7 min; 19% of markets within 48 h, 2% within 6 h; trap guard 6 h), run full floor, ship v0.106.0, restart engine, compare fair ages

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M CLAUDE.md

## Last ten checkpoints
```
  03246fa10 ckpt 3316: v0.105.0 recorded; engine scan note carries timing; TCL16 written from live
  079c76aa5 ckpt 3315: pre-release: v0.105.0: API usage meters stay accurate with the scan on GitH
  14168bece ckpt 3314: pre-ship: v0.105.0: API usage meters stay accurate with the scan on GitHub:
  e1c031802 ckpt 3313: TCL14: usage meters merged (engine usage.json by key id, seed across handov
  da6b9cf15 ckpt 3312: TCL14 written (usage meters accurate with scans on GitHub); v0.104.0 releas
  926e29cfc ckpt 3311: pre-release: v0.104.0: Novig and BoltOdds keys can go to the GitHub engine 
  a50f88791 ckpt 3310: pre-ship: v0.104.0: Novig and BoltOdds keys can go to the GitHub engine (a 
  810737e67 ckpt 3309: TCL13: engine Novig read-only key (CloudNovigKey mint+store, CloudNovig wir
  671773ac5 ckpt 3308: pre-release: v0.103.0: the bid desk's data runs on GitHub: the engine publi
  257a52f09 ckpt 3307: pre-ship: v0.103.0: the bid desk's data runs on GitHub: the engine publishe
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
