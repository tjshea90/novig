# CHECKPOINT 393 — read me first, then TASKS.md

**Written:** 2026-09-25T18:40:47Z · **tests:** all 1 fast checks green
**Branch:** `claude/scanning-performance-markets-3z9pqw` · **builds on:** `0e4bfda` (this checkpoint is the commit after it)

## Just done
pre-release: v0.10.0 (code 14): scans keep running in the background (foreground service), results stream in as Novig prices land (likeliest +EV first, reads overlap the fair odds, pace 4->6/s), NRFI/YRFI + pitcher outs/ER/walks + pass completions via Kalshi, 8 props/game, 300 reads/scan. Gate: CI 36174449917 green on the code (engine+data+app tests, assembleDebug); ship.sh's local Gradle step can't run here (Maven Central 429s, mirror blocked by the auto-mode classifier); versionCode 14 > 13 checked

## Do this next
Trigger release.yml on main, confirm with get_release_by_tag v0.10.0, then bash tools/record-release.sh v0.10.0 14 "..." and send Tj the link

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  6f96317 ckpt 392: CI 36174016113: all compiled; 175/177 data tests green; the 2 runner tests use
  6c401d2 ckpt 391: CI 36173670267: only error was ScannerTest's trailing progress lambda binding 
  7469ec6 ckpt 390: B1-B5 code drafted (not compiled yet: local Gradle blocked by Maven Central 42
  180810d ckpt 389: Logged Tj's 2026-09-25 ~18:05Z request (background scan, speed/streaming, Odds
  3528dbb ckpt 388: Shipped v0.9.0 (code 13): release.yml green, Release confirmed with vigilant-v
  4fe72d2 ckpt 387: pre-release: v0.9.0: sportsbook player props (DraftKings/FanDuel/BetMGM/Caesar
  a621d44 ckpt 386: Docs (RESEARCH §14, BRIEF sportsbook-props decision, CLAUDE surface list, TAS
  5eac253 ckpt 385: P2-P4 tests: OddsApiPropsTest (10: parsing styles, Yes/No, no alternates, free
  f4da200 ckpt 384: P2/P4/P5 wiring: PlayerNames (nicknames incl. Hollywood/Marquise, Last-First, 
  9ac808a ckpt 383: P2 core: TheOddsApiClient.events() (free) + eventOdds() + prop parsing (Over/U
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
