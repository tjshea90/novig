# CHECKPOINT 493 — read me first, then TASKS.md

**Written:** 2026-09-27T14:57:53Z · **tests:** all 1 fast checks green
**Branch:** `claude/vigilant-testing-odds-apis-mpvkc7` · **builds on:** `bff826a` (this checkpoint is the commit after it)

## Just done
pre-release: v0.16.1: fixes 'PropLine props … Unexpected JSON token … bookmakers' (PropLine's game list sends null books; every PropLine field now optional, one odd game never sinks the rest), so PropLine's player props load; scan errors show one short plain line instead of raw JSON. 532 tests (versionCode 28, v0.16.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.16.1), then run: bash tools/record-release.sh v0.16.1 28 "v0.16.1: fixes 'PropLine props … Unexpected JSON token … bookmakers' (PropLine's game list sends null books; every PropLine field now optional, one odd game never sinks the rest), so PropLine's player props load; scan errors show one short plain line instead of raw JSON. 532 tests"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  bff826a ckpt 492: Q1 fixed: PropLine null-tolerant decoding (3 failing-first tests) + readable s
  9babee4 ckpt 491: Wrote Tj's 14:45Z screenshot request (PropLine props JSON error) into TASKS Q1
  b9e3c07 ckpt 490: Answered Tj (no code change): PinnWire setup prompt not needed (use own free k
  dabbe8c ckpt 489: Released v0.16.0 (code 27), recorded; release.yml text updated; P1-P5 ticked
  e4a3561 ckpt 488: pre-release: v0.16.0: Pinnacle player props (free PinnWire key) and 30 sportsb
  9bb3d4a ckpt 487: v0.16.0 (code 27) bumped; full floor 528 tests green + release APK builds; tra
  42475df ckpt 486: P4 fixes 4-5: JsonFileStore keeps every corrupt copy (.corrupt-<time>) + fsync
  7337458 ckpt 485: P4a settle fix done: FreeScores (ESPN+MLB Stats API) + BetGrader + BetSettler 
  652779b ckpt 484: P4 finding: N7 verified live - Novig public catalog 404s settled markets and d
  5f36e51 ckpt 483: P4 fixes 1-2: TeamMatcher token cache (plan matching 165ms->16ms for 61 NCAAF 
```
