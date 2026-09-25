# CHECKPOINT 387 — read me first, then TASKS.md

**Written:** 2026-09-25T16:51:49Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-sports-api-setup-o4mlxh` · **builds on:** `a621d44` (this checkpoint is the commit after it)

## Just done
pre-release: v0.9.0: sportsbook player props (DraftKings/FanDuel/BetMGM/Caesars… via The Odds API per-game odds) inside a per-scan credit budget, devigged per book and averaged/blended with Kalshi; cross-book player-name matching (Last-First, nicknames, initials, spacing); Yes/No and one-sided prop handling; settings for props; credit-estimate fix (versionCode 13, v0.9.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.9.0), then run: bash tools/record-release.sh v0.9.0 13 "v0.9.0: sportsbook player props (DraftKings/FanDuel/BetMGM/Caesars… via The Odds API per-game odds) inside a per-scan credit budget, devigged per book and averaged/blended with Kalshi; cross-book player-name matching (Last-First, nicknames, initials, spacing); Yes/No and one-sided prop handling; settings for props; credit-estimate fix"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  a621d44 ckpt 386: Docs (RESEARCH §14, BRIEF sportsbook-props decision, CLAUDE surface list, TAS
  5eac253 ckpt 385: P2-P4 tests: OddsApiPropsTest (10: parsing styles, Yes/No, no alternates, free
  f4da200 ckpt 384: P2/P4/P5 wiring: PlayerNames (nicknames incl. Hollywood/Marquise, Last-First, 
  9ac808a ckpt 383: P2 core: TheOddsApiClient.events() (free) + eventOdds() + prop parsing (Over/U
  062cf9d ckpt 382: P2 in progress: ScanContext/needsCatalog in ReferenceSource, Scanner waits for
  e812ace ckpt 381: Logged Tj's 2026-09-25 ~16:15Z request (sportsbook props, market average devig
  09d5cbe ckpt 380: SHIPPED v0.8.0 (code 12): CI 36158016075 green, release 36158521051 green, 4.7
  2b65e01 ckpt 379: pre-release: v0.8.0: leagues ordered NFL, NCAAF, MLB, WNBA, NHL first; soccer,
  db386e6 ckpt 378: v0.8.0 ready: 200 tests pass (0 fail, 2 live skipped), release APK builds; doc
  5862aea ckpt 377: A3/A4 + tests green: data 147 (0 fail), app 19. New AltMarketsTest (12: player
```
