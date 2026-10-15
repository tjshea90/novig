# CHECKPOINT 3316 — read me first, then TASKS.md

**Written:** 2026-10-15T02:36:43Z · **tests:** all 4 fast checks green
**Branch:** `ccr-4dcbaa5f-skgw6q` · **builds on:** `121653dad` (this checkpoint is the commit after it)

## Just done
v0.105.0 recorded; engine scan note carries timing; TCL16 written from live numbers (fair median 14.7 min, Novig 16/s all 9,619 books in 537 s)

## Do this next
TCL16a: CloudLanes policy (hot 12h / near 48h / full), nearHours in ScanGuards, engine loop + merge of lane results, tests; then restart engine

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  079c76aa5 ckpt 3315: pre-release: v0.105.0: API usage meters stay accurate with the scan on GitH
  14168bece ckpt 3314: pre-ship: v0.105.0: API usage meters stay accurate with the scan on GitHub:
  e1c031802 ckpt 3313: TCL14: usage meters merged (engine usage.json by key id, seed across handov
  da6b9cf15 ckpt 3312: TCL14 written (usage meters accurate with scans on GitHub); v0.104.0 releas
  926e29cfc ckpt 3311: pre-release: v0.104.0: Novig and BoltOdds keys can go to the GitHub engine 
  a50f88791 ckpt 3310: pre-ship: v0.104.0: Novig and BoltOdds keys can go to the GitHub engine (a 
  810737e67 ckpt 3309: TCL13: engine Novig read-only key (CloudNovigKey mint+store, CloudNovig wir
  671773ac5 ckpt 3308: pre-release: v0.103.0: the bid desk's data runs on GitHub: the engine publi
  257a52f09 ckpt 3307: pre-ship: v0.103.0: the bid desk's data runs on GitHub: the engine publishe
  6a010ec91 ckpt 3306: TCL12: Diagnostics BIDS FROM GITHUB section, Bids tab wording, CLAUDE.md+de
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
