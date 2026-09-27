# CHECKPOINT 529 — read me first, then TASKS.md

**Written:** 2026-09-27T21:28:04Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-game-time-filter-tkml0n` · **builds on:** `80596f1` (this checkpoint is the commit after it)

## Just done
pre-release: v0.17.1: 'Starts within' filter in Vigilant (Any time / 12h / 24h / 48h) on the +EV tab and in Settings; the +EV feed, CNO list, Games board, widgets and scan notifications show only games starting in that window. 619 tests (versionCode 33, v0.17.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.17.1), then run: bash tools/record-release.sh v0.17.1 33 "v0.17.1: 'Starts within' filter in Vigilant (Any time / 12h / 24h / 48h) on the +EV tab and in Settings; the +EV feed, CNO list, Games board, widgets and scan notifications show only games starting in that window. 619 tests"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  80596f1 ckpt 528: G1+G2: start-time window (Any/12/24/48h) on feed, CNO, Games, widgets, notific
  5d4dc1d ckpt 527: Wrote Tj's game start-time filter request into TASKS.md (G1-G3)
  0974b60 ckpt 526: SHIPPED v0.17.0 code 32 (V1-V5 ticked): Vigilant MGM + Vigilant both on https:
  bbd955f ckpt 525: pre-release: v0.17.0: Vigilant MGM, the same +EV scanner for BetMGM as its own
  f68ecd4 ckpt 524: V1-V4 done + docs (RESEARCH §25, BRIEF decision, CLAUDE surface), version v0.
  55958db ckpt 523: V3 app: AppBook (BuildConfig.BOOK) + mgm module (com.tjshea.vigilant.betmgm, c
  56eb079 ckpt 522: V1 data layer + V2 links: data/book (Sportsbook, BookBoard, SportsbookScanner,
```
