# CHECKPOINT 569 — read me first, then TASKS.md

**Written:** 2026-09-28T08:05:41Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-api-coverage-d7iwyq` · **builds on:** `19ebb4b` (this checkpoint is the commit after it)

## Just done
pre-release: v0.19.2: bets shown mid-scan stay: a league's bets wait until all its fair-odds sources answer; scans use only book prices with 2+ minutes of freshness left; bets hidden for old odds are counted on the feed. 694 tests (versionCode 37, v0.19.2)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.19.2), then run: bash tools/record-release.sh v0.19.2 37 "v0.19.2: bets shown mid-scan stay: a league's bets wait until all its fair-odds sources answer; scans use only book prices with 2+ minutes of freshness left; bets hidden for old odds are counted on the feed. 694 tests"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  19ebb4b ckpt 568: pre-release v0.19.2 (37): steady feed (hold bets until fair sources answer, 2-
  f22cd3f ckpt 567: Recorded Tj's 'bets appeared then disappeared' question as Q1-Q2
  3738919 ckpt 566: Released Vigilant v0.19.1 (code 36): release.yml run 36392649770 green, tag v0
  d6e8948 ckpt 565: pre-release v0.19.1 (36): floor 689 green (engine 39, data 445/10 live skipped
  d0342a0 ckpt 564: M1+M2 done: week-ahead default, later-games count, DELAYED, strike guard, /v3/
  2d1fca0 ckpt 563: M1: Days ahead 7 default (+schema 8 moves a saved 3), all events read so later
  8f76985 ckpt 562: Recorded Tj's 'way more than 7 games' + Novig docs request as M1-M3
  ac363cb ckpt 561: Released Vigilant v0.19.0 (code 35): release.yml run 36389431089 green, tag v0
  627a6eb ckpt 560: pre-release: v0.19.0: keyed scans load their prices through Novig's live feed 
  4fca994 ckpt 559: pre-release v0.19.0 (code 35): floor 677 tests green (engine 39, data 434/9 li
```
