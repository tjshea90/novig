# CHECKPOINT 390 — read me first, then TASKS.md

**Written:** 2026-09-25T18:28:35Z · **tests:** all 1 fast checks green
**Branch:** `claude/scanning-performance-markets-3z9pqw` · **builds on:** `2a1f5ab` (this checkpoint is the commit after it)

## Just done
B1-B5 code drafted (not compiled yet: local Gradle blocked by Maven Central 429): ScanRunner+ScanService (FGS dataSync, wake lock, notifications), streaming pipelined Scanner with fetch priority, RateGate ramp 4->6/s public, keyed 14/s, Kalshi NRFI+pitcher outs/ER/walks+pass completions, defaults props 8 / books 300 (schema 4)

## Do this next
Push and let CI compile/test; fix whatever CI finds; then screenshots/docs (RESEARCH §15, NOVIG_API, BRIEF, CLAUDE surface), ship v0.10.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/src/main/kotlin/com/tjshea/vigilant/app/MainActivity.kt

## Last ten checkpoints
```
  180810d ckpt 389: Logged Tj's 2026-09-25 ~18:05Z request (background scan, speed/streaming, Odds
  3528dbb ckpt 388: Shipped v0.9.0 (code 13): release.yml green, Release confirmed with vigilant-v
  4fe72d2 ckpt 387: pre-release: v0.9.0: sportsbook player props (DraftKings/FanDuel/BetMGM/Caesar
  a621d44 ckpt 386: Docs (RESEARCH §14, BRIEF sportsbook-props decision, CLAUDE surface list, TAS
  5eac253 ckpt 385: P2-P4 tests: OddsApiPropsTest (10: parsing styles, Yes/No, no alternates, free
  f4da200 ckpt 384: P2/P4/P5 wiring: PlayerNames (nicknames incl. Hollywood/Marquise, Last-First, 
  9ac808a ckpt 383: P2 core: TheOddsApiClient.events() (free) + eventOdds() + prop parsing (Over/U
  062cf9d ckpt 382: P2 in progress: ScanContext/needsCatalog in ReferenceSource, Scanner waits for
  e812ace ckpt 381: Logged Tj's 2026-09-25 ~16:15Z request (sportsbook props, market average devig
  09d5cbe ckpt 380: SHIPPED v0.8.0 (code 12): CI 36158016075 green, release 36158521051 green, 4.7
```

(23 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
