# CHECKPOINT 419 — read me first, then TASKS.md

**Written:** 2026-09-26T19:06:47Z · **tests:** all 1 fast checks green
**Branch:** `claude/odds-display-integration-8qnsaq` · **builds on:** `726bd85` (this checkpoint is the commit after it)

## Just done
pre-release: v0.14.0: CNO scanner made accurate and standalone: CNO-only mode (rest of the app asleep); Conservative worst-case devig, odds up to +150, 5+ books, checked rows; tap a bet for every book's odds and Vigilant's own verdict (also in the widget: Books); real-time/5 s/15 s refresh; Open in Novig to the game. 348 tests (versionCode 18, v0.14.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.14.0), then run: bash tools/record-release.sh v0.14.0 18 "v0.14.0: CNO scanner made accurate and standalone: CNO-only mode (rest of the app asleep); Conservative worst-case devig, odds up to +150, 5+ books, checked rows; tap a bet for every book's odds and Vigilant's own verdict (also in the widget: Books); real-time/5 s/15 s refresh; Open in Novig to the game. 348 tests"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  85c0abd ckpt 418: F6 full test of the CNO scanner: fixed UW-WC label, judged-book check, stuck p
  662465f ckpt 417: F2-F5 app side built: ScannerMode (both/vigilant/cno) w/ migration from miniSo
  a424bc9 ckpt 416: F2-F5 data layer: CnoFilters posted in CNO's form (Conservative WC default, +1
  4891630 ckpt 415: F1 research done: RESEARCH.md §19 (EV = CNO fair vs Novig price verified; dev
  477b55b ckpt 414: Logged Tj's 2026-09-26 ~18:20Z request (accurate/standalone/faster CNO scanner
  b64acac ckpt 413: SHIPPED v0.13.0 (code 17): CrazyNinjaOdds list in CNO tab + mini window; CI 36
  7b18eb6 ckpt 412: pre-release: v0.13.0: CrazyNinjaOdds' +EV list (your Shared View link) in a CN
  84d75e5 ckpt 411: E2 done: CrazyNinjaOdds list in a CNO tab and the mini window (v0.13.0 code 17
  73e8961 ckpt 410: E2b app side built: CNO tab + sheet (Kelly stake, CNO game link, Open Novig), 
  9111748 ckpt 409: E2a: CNO data layer (CnoView/CnoPage/CnoClient/CnoFeed) + 26 tests green + liv
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
