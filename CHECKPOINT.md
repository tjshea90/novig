# CHECKPOINT 490 — read me first, then TASKS.md

**Written:** 2026-09-27T14:37:53Z · **tests:** all 1 fast checks green
**Branch:** `claude/vigilant-testing-odds-apis-mpvkc7` · **builds on:** `853a47b` (this checkpoint is the commit after it)

## Just done
Answered Tj (no code change): PinnWire setup prompt not needed (use own free key, never paste keys in chat - INBOX is public); PropLine prop grading is paid (Hobby $9/mo; free tier redacts results), app already grades props free from ESPN/MLB box scores (v0.16.0, live 8/8)

## Do this next
P6 verify PropLine live after 00:00Z (send_later set for 00:15Z); offer PropLine grading as a paid fallback only if Tj's Tracker shows many bets stuck open

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  dabbe8c ckpt 489: Released v0.16.0 (code 27), recorded; release.yml text updated; P1-P5 ticked
  e4a3561 ckpt 488: pre-release: v0.16.0: Pinnacle player props (free PinnWire key) and 30 sportsb
  9bb3d4a ckpt 487: v0.16.0 (code 27) bumped; full floor 528 tests green + release APK builds; tra
  42475df ckpt 486: P4 fixes 4-5: JsonFileStore keeps every corrupt copy (.corrupt-<time>) + fsync
  7337458 ckpt 485: P4a settle fix done: FreeScores (ESPN+MLB Stats API) + BetGrader + BetSettler 
  652779b ckpt 484: P4 finding: N7 verified live - Novig public catalog 404s settled markets and d
  5f36e51 ckpt 483: P4 fixes 1-2: TeamMatcher token cache (plan matching 165ms->16ms for 61 NCAAF 
  b9823b7 ckpt 482: P3 done: PinnWire (Pinnacle props) + PropLine sources wired end to end; full f
  ea98af9 ckpt 481: P3c app wiring: UiState pinnwire/propline keys + keysOf/withKeys, AppContainer
  9dc7beb ckpt 480: P3a+P3b data layer: PinnWire Pinnacle props + PropLine client/props source, Sc
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
