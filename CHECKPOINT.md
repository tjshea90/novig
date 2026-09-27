# CHECKPOINT 488 — read me first, then TASKS.md

**Written:** 2026-09-27T07:12:54Z · **tests:** all 1 fast checks green
**Branch:** `claude/vigilant-testing-odds-apis-mpvkc7` · **builds on:** `9bb3d4a` (this checkpoint is the commit after it)

## Just done
pre-release: v0.16.0: Pinnacle player props (free PinnWire key) and 30 sportsbooks (free PropLine key) as fair-odds sources; bets now settle from final scores (ESPN, MLB Stats API) - Novig's catalog forgets finished games, so v0.15.6 never settled; scans plan ~10x faster; Novig's board waits out a short slow-down; corrupt save files always kept. 528 tests (versionCode 27, v0.16.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.16.0), then run: bash tools/record-release.sh v0.16.0 27 "v0.16.0: Pinnacle player props (free PinnWire key) and 30 sportsbooks (free PropLine key) as fair-odds sources; bets now settle from final scores (ESPN, MLB Stats API) - Novig's catalog forgets finished games, so v0.15.6 never settled; scans plan ~10x faster; Novig's board waits out a short slow-down; corrupt save files always kept. 528 tests"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  9bb3d4a ckpt 487: v0.16.0 (code 27) bumped; full floor 528 tests green + release APK builds; tra
  42475df ckpt 486: P4 fixes 4-5: JsonFileStore keeps every corrupt copy (.corrupt-<time>) + fsync
  7337458 ckpt 485: P4a settle fix done: FreeScores (ESPN+MLB Stats API) + BetGrader + BetSettler 
  652779b ckpt 484: P4 finding: N7 verified live - Novig public catalog 404s settled markets and d
  5f36e51 ckpt 483: P4 fixes 1-2: TeamMatcher token cache (plan matching 165ms->16ms for 61 NCAAF 
  b9823b7 ckpt 482: P3 done: PinnWire (Pinnacle props) + PropLine sources wired end to end; full f
  ea98af9 ckpt 481: P3c app wiring: UiState pinnwire/propline keys + keysOf/withKeys, AppContainer
  9dc7beb ckpt 480: P3a+P3b data layer: PinnWire Pinnacle props + PropLine client/props source, Sc
  14e3f27 ckpt 479: P3a data: PinnapiClient hosts (PinnWire first, x-api-key, include_specials whe
  73c8699 ckpt 478: P2 research written: RESEARCH.md §22 (PinnWire free Pinnacle props measured l
```
