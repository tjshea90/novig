# CHECKPOINT 418 — read me first, then TASKS.md

**Written:** 2026-09-26T19:02:20Z · **tests:** all 1 fast checks green
**Branch:** `claude/odds-display-integration-8qnsaq` · **builds on:** `f47bef4` (this checkpoint is the commit after it)

## Just done
F6 full test of the CNO scanner: fixed UW-WC label, judged-book check, stuck polling on fixed intervals, cache write throttling, widget Books pinned by key + self-loading, compound Last Updated, badge clock recomposition, $it chip labels; each with a failing-first test; v0.14.0 code 18; forced full rerun 348 tests 0 failed 3 skipped exit 0; live smoke green; docs updated

## Do this next
Wait for CI green on this commit, then ship.sh, trigger release.yml, confirm v0.14.0, record-release.sh, send Tj the link

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M BRIEF.md
     M CHECKPOINT.md
     M CLAUDE.md
     M RESEARCH.md
     M TASKS.md

## Last ten checkpoints
```
  662465f ckpt 417: F2-F5 app side built: ScannerMode (both/vigilant/cno) w/ migration from miniSo
  a424bc9 ckpt 416: F2-F5 data layer: CnoFilters posted in CNO's form (Conservative WC default, +1
  4891630 ckpt 415: F1 research done: RESEARCH.md §19 (EV = CNO fair vs Novig price verified; dev
  477b55b ckpt 414: Logged Tj's 2026-09-26 ~18:20Z request (accurate/standalone/faster CNO scanner
  b64acac ckpt 413: SHIPPED v0.13.0 (code 17): CrazyNinjaOdds list in CNO tab + mini window; CI 36
  7b18eb6 ckpt 412: pre-release: v0.13.0: CrazyNinjaOdds' +EV list (your Shared View link) in a CN
  84d75e5 ckpt 411: E2 done: CrazyNinjaOdds list in a CNO tab and the mini window (v0.13.0 code 17
  73e8961 ckpt 410: E2b app side built: CNO tab + sheet (Kelly stake, CNO game link, Open Novig), 
  9111748 ckpt 409: E2a: CNO data layer (CnoView/CnoPage/CnoClient/CnoFeed) + 26 tests green + liv
  3cdc233 ckpt 408: E1 done: RESEARCH.md §18 (how CNO's page/filters/table work, its terms forbid
```

(10 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
