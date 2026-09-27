# CHECKPOINT 486 — read me first, then TASKS.md

**Written:** 2026-09-27T07:02:24Z · **tests:** all 1 fast checks green
**Branch:** `claude/vigilant-testing-odds-apis-mpvkc7` · **builds on:** `ff214f6` (this checkpoint is the commit after it)

## Just done
P4 fixes 4-5: JsonFileStore keeps every corrupt copy (.corrupt-<time>) + fsync before rename (test 'a second corrupt file never replaces the first' fails pre-fix); settle wording (final scores) in Tracker/worker/toast; props hint names Pinnacle

## Do this next
P4: finish app-side sweep (ScanService/MiniWindow/Floating quick check, ScanText), docs (CLAUDE.md surface list, BRIEF), then P5 full floor + screenshots + ship v0.16.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/src/main/kotlin/com/tjshea/vigilant/app/ui/SettingsScreen.kt

## Last ten checkpoints
```
  7337458 ckpt 485: P4a settle fix done: FreeScores (ESPN+MLB Stats API) + BetGrader + BetSettler 
  652779b ckpt 484: P4 finding: N7 verified live - Novig public catalog 404s settled markets and d
  5f36e51 ckpt 483: P4 fixes 1-2: TeamMatcher token cache (plan matching 165ms->16ms for 61 NCAAF 
  b9823b7 ckpt 482: P3 done: PinnWire (Pinnacle props) + PropLine sources wired end to end; full f
  ea98af9 ckpt 481: P3c app wiring: UiState pinnwire/propline keys + keysOf/withKeys, AppContainer
  9dc7beb ckpt 480: P3a+P3b data layer: PinnWire Pinnacle props + PropLine client/props source, Sc
  14e3f27 ckpt 479: P3a data: PinnapiClient hosts (PinnWire first, x-api-key, include_specials whe
  73c8699 ckpt 478: P2 research written: RESEARCH.md §22 (PinnWire free Pinnacle props measured l
  fcab184 ckpt 477: P1 done: local SDK+mirror, baseline 496 tests green; P2 research in progress (
  4d121cf ckpt 476: Wrote Tj's 06:14Z request into TASKS.md as P1-P5
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
