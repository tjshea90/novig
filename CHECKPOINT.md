# CHECKPOINT 417 — read me first, then TASKS.md

**Written:** 2026-09-26T18:53:13Z · **tests:** all 1 fast checks green
**Branch:** `claude/odds-display-integration-8qnsaq` · **builds on:** `984159d` (this checkpoint is the commit after it)

## Just done
F2-F5 app side built: ScannerMode (both/vigilant/cno) w/ migration from miniSource/cnoEnabled, CNO-only hides +EV/Games tabs + Vigilant settings and guards scan/recheck; CNO filters in Settings (devig, +150 cap, books, EV, complete book, rows, refresh real-time/5/15/30/60/tap w/ data use); CNO tab shows checked picks + hidden counts + stuck/mismatch banners + CNO-only chip; bet detail w/ every book, Vigilant worst-case verdict, Open in Novig (deeplink); widget Books/List + Next; MiniWindowTest 9 + ScreenshotTest 50 green

## Do this next
Look at screenshots (8_cno, 8e_cno_detail, 7f_mini_window_books, 5c_settings_cno_only); then F6 full test of the CNO scanner

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  a424bc9 ckpt 416: F2-F5 data layer: CnoFilters posted in CNO's form (Conservative WC default, +1
  4891630 ckpt 415: F1 research done: RESEARCH.md §19 (EV = CNO fair vs Novig price verified; dev
  477b55b ckpt 414: Logged Tj's 2026-09-26 ~18:20Z request (accurate/standalone/faster CNO scanner
  b64acac ckpt 413: SHIPPED v0.13.0 (code 17): CrazyNinjaOdds list in CNO tab + mini window; CI 36
  7b18eb6 ckpt 412: pre-release: v0.13.0: CrazyNinjaOdds' +EV list (your Shared View link) in a CN
  84d75e5 ckpt 411: E2 done: CrazyNinjaOdds list in a CNO tab and the mini window (v0.13.0 code 17
  73e8961 ckpt 410: E2b app side built: CNO tab + sheet (Kelly stake, CNO game link, Open Novig), 
  9111748 ckpt 409: E2a: CNO data layer (CnoView/CnoPage/CnoClient/CnoFeed) + 26 tests green + liv
  3cdc233 ckpt 408: E1 done: RESEARCH.md §18 (how CNO's page/filters/table work, its terms forbid
  e1facc2 ckpt 407: Logged Tj's 2026-09-26T15:34Z request (CrazyNinjaOdds' positive-EV rows in the
```

(15 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
