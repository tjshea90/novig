# CHECKPOINT 458 — read me first, then TASKS.md

**Written:** 2026-09-27T01:51:46Z · **tests:** all 1 fast checks green
**Branch:** `claude/cno-scanner-enhancements-tj52i1` · **builds on:** `d1ad63e` (this checkpoint is the commit after it)

## Just done
pre-release: v0.15.4: bet slips open without CNO (Novig's own catalog, 60/60 exact live); the widget's top-bar switch adds Vigilant's own scan (Both), a bet both scanners find shows once, optional rescan while the widget is open; CNO bets show Novig's price now (from Novig's order book) with the EV at it; the bet finder is paced and backs off when Novig says slow down. 474 tests (versionCode 24, v0.15.4)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.15.4), then run: bash tools/record-release.sh v0.15.4 24 "v0.15.4: bet slips open without CNO (Novig's own catalog, 60/60 exact live); the widget's top-bar switch adds Vigilant's own scan (Both), a bet both scanners find shows once, optional rescan while the widget is open; CNO bets show Novig's price now (from Novig's order book) with the EV at it; the bet finder is paced and backs off when Novig says slow down. 474 tests"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  d1ad63e ckpt 457: L1-L3 done; v0.15.4 code 24; forced floor 474 green (engine 39, data 305, app 
  3021174 ckpt 456: Logged Tj's live +EV request as M1-M3 (next version, after L4 ships); L3 in pr
  82074cb ckpt 455: L2 done: widget top-bar switch CNO only/Both, same bet shown once (outcome mat
  5cc41a5 ckpt 454: L1 done: taps race CNO + Novig catalog (first exact wins); links lane catalog-
  8b2086b ckpt 453: Logged Tj's 01:18Z requests as L1-L4 in TASKS.md
  8eb5cb1 ckpt 452: K10 done: v0.15.3 shipped (CI green 7b9636f, Release published, recorded); K1-
  7b9636f ckpt 451: pre-release: v0.15.3: full test: CNO pages parsed off the main thread (smoothe
  7431be3 ckpt 450: K10b/c done; v0.15.3 code 23 bumped; forced floor 446 green; v0.15.2 released 
  732628d ckpt 449: K10a: parse off main (CnoClient/PlayerTeams), pause fixes (links, mid-read), v
  b39913d ckpt 448: CI fix: CnoNetworkTest dead-connection retry pinned to 127.0.0.1 (CI runners r
```
