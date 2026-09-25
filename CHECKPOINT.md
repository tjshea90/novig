# CHECKPOINT 391 — read me first, then TASKS.md

**Written:** 2026-09-25T18:31:46Z · **tests:** all 1 fast checks green
**Branch:** `claude/scanning-performance-markets-3z9pqw` · **builds on:** `e7b5e8d` (this checkpoint is the commit after it)

## Just done
CI 36173670267: only error was ScannerTest's trailing progress lambda binding to the new onPartial param (fixed by name). Added: mid-scan fair-age rule per provider re-use window, streaming feed copy ('so far', switch-apps hint), ScanTextTest, streaming screenshots

## Do this next
Wait for CI on this commit; fix anything red; then docs (RESEARCH §15 draft in scratchpad research15.md, NOVIG_API pacing, BRIEF, CLAUDE surface list), versionCode 14 / 0.10.0, ship

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  7469ec6 ckpt 390: B1-B5 code drafted (not compiled yet: local Gradle blocked by Maven Central 42
  180810d ckpt 389: Logged Tj's 2026-09-25 ~18:05Z request (background scan, speed/streaming, Odds
  3528dbb ckpt 388: Shipped v0.9.0 (code 13): release.yml green, Release confirmed with vigilant-v
  4fe72d2 ckpt 387: pre-release: v0.9.0: sportsbook player props (DraftKings/FanDuel/BetMGM/Caesar
  a621d44 ckpt 386: Docs (RESEARCH §14, BRIEF sportsbook-props decision, CLAUDE surface list, TAS
  5eac253 ckpt 385: P2-P4 tests: OddsApiPropsTest (10: parsing styles, Yes/No, no alternates, free
  f4da200 ckpt 384: P2/P4/P5 wiring: PlayerNames (nicknames incl. Hollywood/Marquise, Last-First, 
  9ac808a ckpt 383: P2 core: TheOddsApiClient.events() (free) + eventOdds() + prop parsing (Over/U
  062cf9d ckpt 382: P2 in progress: ScanContext/needsCatalog in ReferenceSource, Scanner waits for
  e812ace ckpt 381: Logged Tj's 2026-09-25 ~16:15Z request (sportsbook props, market average devig
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
