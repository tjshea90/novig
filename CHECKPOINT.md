# CHECKPOINT 398 — read me first, then TASKS.md

**Written:** 2026-09-26T02:05:42Z · **tests:** all 1 fast checks green
**Branch:** `claude/positive-ev-betting-research-dkp593` · **builds on:** `2e698b7` (this checkpoint is the commit after it)

## Just done
C2 data layer: ScanSettings.outlierGuard (default on) + maxOdds (default +1000, feed filter); Opportunity.bestBid/makerBid/priceIsOld; CrossCheck.devigger (CNO prefilled link); Scanner.recheck (<=40 books, no fair-odds calls); BetTracker averages skip VOID (failed pre-fix). ResearchFeaturesTest (6), ScannerTest recheck (2), BetTrackerTest void. engine+data green.

## Do this next
App: MainViewModel.recheck + status; FeedScreen recheck button, old-price flag/banner by book age; OpportunitySheet recheck, maker bid, CNO link; Settings: outlier guard + max odds; screenshots; then RESEARCH.md §16

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  4284bdd ckpt 397: C1/C2: fixed stale-fair pricing in Scanner (final result + reprice only from y
  b35fb87 ckpt 396: C1 started: local SDK + Maven/Robolectric mirror set up (build trap 6); full f
  449efd7 ckpt 395: Logged Tj's 2026-09-26 request (full tests, feature/scan improvements, OddsAss
  aa9d099 ckpt 394: SHIPPED v0.10.0 (code 14): release 36174969512 green, Release confirmed with v
  1e820db ckpt 393: pre-release: v0.10.0 (code 14): scans keep running in the background (foregrou
  6f96317 ckpt 392: CI 36174016113: all compiled; 175/177 data tests green; the 2 runner tests use
  6c401d2 ckpt 391: CI 36173670267: only error was ScannerTest's trailing progress lambda binding 
  7469ec6 ckpt 390: B1-B5 code drafted (not compiled yet: local Gradle blocked by Maven Central 42
  180810d ckpt 389: Logged Tj's 2026-09-25 ~18:05Z request (background scan, speed/streaming, Odds
  3528dbb ckpt 388: Shipped v0.9.0 (code 13): release.yml green, Release confirmed with vigilant-v
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
