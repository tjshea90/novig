# CHECKPOINT 416 — read me first, then TASKS.md

**Written:** 2026-09-26T18:41:54Z · **tests:** all 1 fast checks green
**Branch:** `claude/odds-display-integration-8qnsaq` · **builds on:** `31f4b16` (this checkpoint is the commit after it)

## Just done
F2-F5 data layer: CnoFilters posted in CNO's form (Conservative WC default, +150, 5 books, 1% EV, 50 rows, complete book; stricter link values kept), CnoChecks (mismatch, one-way, books, odds, >20% EV, live fee), CnoBooks (game page parse, other side, worst-case check w/ verdict), CnoFeed (3 s gap, real time, 5/15 s, backoff, books cache, Novig link), compound Last Updated fix; 238 data tests green; live smoke green (48 rows C-WC, refresh 1 req, top bet CONFIRMED 5 two-sided, novigapp link)

## Do this next
App side: ScanSettings scanner mode (both/vigilant/cno) + CNO filter settings + migration; MainViewModel (scan guard, screened picks, books, Novig link); CNO tab (screened list, hidden counts, stuck banner, sheet with books/verdict/Open in Novig); tabs per mode; widget Books action; Settings restructure

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  4891630 ckpt 415: F1 research done: RESEARCH.md §19 (EV = CNO fair vs Novig price verified; dev
  477b55b ckpt 414: Logged Tj's 2026-09-26 ~18:20Z request (accurate/standalone/faster CNO scanner
  b64acac ckpt 413: SHIPPED v0.13.0 (code 17): CrazyNinjaOdds list in CNO tab + mini window; CI 36
  7b18eb6 ckpt 412: pre-release: v0.13.0: CrazyNinjaOdds' +EV list (your Shared View link) in a CN
  84d75e5 ckpt 411: E2 done: CrazyNinjaOdds list in a CNO tab and the mini window (v0.13.0 code 17
  73e8961 ckpt 410: E2b app side built: CNO tab + sheet (Kelly stake, CNO game link, Open Novig), 
  9111748 ckpt 409: E2a: CNO data layer (CnoView/CnoPage/CnoClient/CnoFeed) + 26 tests green + liv
  3cdc233 ckpt 408: E1 done: RESEARCH.md §18 (how CNO's page/filters/table work, its terms forbid
  e1facc2 ckpt 407: Logged Tj's 2026-09-26T15:34Z request (CrazyNinjaOdds' positive-EV rows in the
  7104c43 ckpt 406: SHIPPED v0.12.0 (code 16): mini window over Novig; CI 36214953291 + release 36
```

(20 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
