# CHECKPOINT 401 — read me first, then TASKS.md

**Written:** 2026-09-26T02:21:18Z · **tests:** all 1 fast checks green
**Branch:** `claude/positive-ev-betting-research-dkp593` · **builds on:** `c97bc57` (this checkpoint is the commit after it)

## Just done
pre-release: v0.11.0: full-test fixes (stale fair odds never price the feed; voided bets out of tracker averages; old Novig prices flagged); Recheck (re-read the feed's or one bet's Novig prices in seconds); outlier guard (min of mean/median, 3+ books) and +1000 longest-odds cap from CrazyNinjaOdds' method; maker bid on Novig's grid; CrazyNinjaOdds devigger cross-check link; RESEARCH.md §16 (versionCode 15, v0.11.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.11.0), then run: bash tools/record-release.sh v0.11.0 15 "v0.11.0: full-test fixes (stale fair odds never price the feed; voided bets out of tracker averages; old Novig prices flagged); Recheck (re-read the feed's or one bet's Novig prices in seconds); outlier guard (min of mean/median, 3+ books) and +1000 longest-odds cap from CrazyNinjaOdds' method; maker bid on Novig's grid; CrazyNinjaOdds devigger cross-check link; RESEARCH.md §16"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  420fc11 ckpt 400: C1-C5 done: RESEARCH.md §16 (CNO real Novig +EV at $5-15 depth; OddsAssist he
  efee7d7 ckpt 399: C2 app layer: recheck (feed 'Recheck prices' + sheet 'Recheck price' + old-pri
  1e2cb8e ckpt 398: C2 data layer: ScanSettings.outlierGuard (default on) + maxOdds (default +1000
  4284bdd ckpt 397: C1/C2: fixed stale-fair pricing in Scanner (final result + reprice only from y
  b35fb87 ckpt 396: C1 started: local SDK + Maven/Robolectric mirror set up (build trap 6); full f
  449efd7 ckpt 395: Logged Tj's 2026-09-26 request (full tests, feature/scan improvements, OddsAss
  aa9d099 ckpt 394: SHIPPED v0.10.0 (code 14): release 36174969512 green, Release confirmed with v
  1e820db ckpt 393: pre-release: v0.10.0 (code 14): scans keep running in the background (foregrou
  6f96317 ckpt 392: CI 36174016113: all compiled; 175/177 data tests green; the 2 runner tests use
  6c401d2 ckpt 391: CI 36173670267: only error was ScannerTest's trailing progress lambda binding 
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
