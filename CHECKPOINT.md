# CHECKPOINT 422 — read me first, then TASKS.md

**Written:** 2026-09-26T19:48:15Z · **tests:** all 1 fast checks green
**Branch:** `claude/odds-display-integration-8qnsaq` · **builds on:** `e836253` (this checkpoint is the commit after it)

## Just done
G1-G2: mini window pick names readable (own Surface + full-contrast bold name; failing-first pixel test showed 0.00..0.05 luminance on v0.14.0) and never cut at the line (splitPick/nameChoices, two-line layout when tiny); widget screenshots drawn like the real window; v0.14.1 code 19; forced rerun 354 tests 0 failed exit 0

## Do this next
G3: CI green on this commit, ship.sh, release.yml, confirm v0.14.1, record-release, send link

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M RESEARCH.md
     M TASKS.md

## Last ten checkpoints
```
  27d02b7 ckpt 421: Logged Tj's request: the mini window doesn't show what the pick is (screenshot
  7b222d1 ckpt 420: SHIPPED v0.14.0 (code 18): accurate, standalone CNO scanner; CI 36264649883 gr
  c727840 ckpt 419: pre-release: v0.14.0: CNO scanner made accurate and standalone: CNO-only mode 
  85c0abd ckpt 418: F6 full test of the CNO scanner: fixed UW-WC label, judged-book check, stuck p
  662465f ckpt 417: F2-F5 app side built: ScannerMode (both/vigilant/cno) w/ migration from miniSo
  a424bc9 ckpt 416: F2-F5 data layer: CnoFilters posted in CNO's form (Conservative WC default, +1
  4891630 ckpt 415: F1 research done: RESEARCH.md §19 (EV = CNO fair vs Novig price verified; dev
  477b55b ckpt 414: Logged Tj's 2026-09-26 ~18:20Z request (accurate/standalone/faster CNO scanner
  b64acac ckpt 413: SHIPPED v0.13.0 (code 17): CrazyNinjaOdds list in CNO tab + mini window; CI 36
  7b18eb6 ckpt 412: pre-release: v0.13.0: CrazyNinjaOdds' +EV list (your Shared View link) in a CN
```

(14 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
