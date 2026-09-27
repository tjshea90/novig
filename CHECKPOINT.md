# CHECKPOINT 492 — read me first, then TASKS.md

**Written:** 2026-09-27T14:51:57Z · **tests:** all 1 fast checks green
**Branch:** `claude/vigilant-testing-odds-apis-mpvkc7` · **builds on:** `84c7109` (this checkpoint is the commit after it)

## Just done
Q1 fixed: PropLine null-tolerant decoding (3 failing-first tests) + readable scan errors; v0.16.1 code 28 bumped

## Do this next
Q2: full floor + assembleRelease, CI green, ship.sh, release.yml, record, link

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  9babee4 ckpt 491: Wrote Tj's 14:45Z screenshot request (PropLine props JSON error) into TASKS Q1
  b9e3c07 ckpt 490: Answered Tj (no code change): PinnWire setup prompt not needed (use own free k
  dabbe8c ckpt 489: Released v0.16.0 (code 27), recorded; release.yml text updated; P1-P5 ticked
  e4a3561 ckpt 488: pre-release: v0.16.0: Pinnacle player props (free PinnWire key) and 30 sportsb
  9bb3d4a ckpt 487: v0.16.0 (code 27) bumped; full floor 528 tests green + release APK builds; tra
  42475df ckpt 486: P4 fixes 4-5: JsonFileStore keeps every corrupt copy (.corrupt-<time>) + fsync
  7337458 ckpt 485: P4a settle fix done: FreeScores (ESPN+MLB Stats API) + BetGrader + BetSettler 
  652779b ckpt 484: P4 finding: N7 verified live - Novig public catalog 404s settled markets and d
  5f36e51 ckpt 483: P4 fixes 1-2: TeamMatcher token cache (plan matching 165ms->16ms for 61 NCAAF 
  b9823b7 ckpt 482: P3 done: PinnWire (Pinnacle props) + PropLine sources wired end to end; full f
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
