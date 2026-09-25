# CHECKPOINT 392 — read me first, then TASKS.md

**Written:** 2026-09-25T18:35:54Z · **tests:** all 1 fast checks green
**Branch:** `claude/scanning-performance-markets-3z9pqw` · **builds on:** `75af4dd` (this checkpoint is the commit after it)

## Just done
CI 36174016113: all compiled; 175/177 data tests green; the 2 runner tests used backgroundScope (advanceUntilIdle ignores it) - now the test scope. Hardened ScanService startForeground; docs: RESEARCH §15 (+live pacing 240 books 0 refusals, Kalshi quote quality), BRIEF, NOVIG_API, CLAUDE surface; v0.10.0 code 14

## Do this next
Wait for CI; if green tick TASKS B1-B5 (patch4.py in scratchpad), then ship.sh + release.yml + record-release

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M BRIEF.md
     M CHECKPOINT.md
     M CLAUDE.md
     M NOVIG_API.md
     M RESEARCH.md
     M app/build.gradle.kts
     M app/src/main/kotlin/com/tjshea/vigilant/app/ScanService.kt

## Last ten checkpoints
```
  6c401d2 ckpt 391: CI 36173670267: only error was ScannerTest's trailing progress lambda binding 
  7469ec6 ckpt 390: B1-B5 code drafted (not compiled yet: local Gradle blocked by Maven Central 42
  180810d ckpt 389: Logged Tj's 2026-09-25 ~18:05Z request (background scan, speed/streaming, Odds
  3528dbb ckpt 388: Shipped v0.9.0 (code 13): release.yml green, Release confirmed with vigilant-v
  4fe72d2 ckpt 387: pre-release: v0.9.0: sportsbook player props (DraftKings/FanDuel/BetMGM/Caesar
  a621d44 ckpt 386: Docs (RESEARCH §14, BRIEF sportsbook-props decision, CLAUDE surface list, TAS
  5eac253 ckpt 385: P2-P4 tests: OddsApiPropsTest (10: parsing styles, Yes/No, no alternates, free
  f4da200 ckpt 384: P2/P4/P5 wiring: PlayerNames (nicknames incl. Hollywood/Marquise, Last-First, 
  9ac808a ckpt 383: P2 core: TheOddsApiClient.events() (free) + eventOdds() + prop parsing (Over/U
  062cf9d ckpt 382: P2 in progress: ScanContext/needsCatalog in ReferenceSource, Scanner waits for
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
