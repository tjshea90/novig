# CHECKPOINT 483 — read me first, then TASKS.md

**Written:** 2026-09-27T06:47:04Z · **tests:** all 1 fast checks green
**Branch:** `claude/vigilant-testing-odds-apis-mpvkc7` · **builds on:** `c931939` (this checkpoint is the commit after it)

## Just done
P4 fixes 1-2: TeamMatcher token cache (plan matching 165ms->16ms for 61 NCAAF games x4 feeds; test 'a name is tokenized once'), Novig board retries a short 429 instead of failing the scan (test 'a short 429 on the board is waited out'); both fail pre-fix

## Do this next
P4 sweep continues: ScanRunner, cno/*, tracker/*, teams, store, then app/*

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  b9823b7 ckpt 482: P3 done: PinnWire (Pinnacle props) + PropLine sources wired end to end; full f
  ea98af9 ckpt 481: P3c app wiring: UiState pinnwire/propline keys + keysOf/withKeys, AppContainer
  9dc7beb ckpt 480: P3a+P3b data layer: PinnWire Pinnacle props + PropLine client/props source, Sc
  14e3f27 ckpt 479: P3a data: PinnapiClient hosts (PinnWire first, x-api-key, include_specials whe
  73c8699 ckpt 478: P2 research written: RESEARCH.md §22 (PinnWire free Pinnacle props measured l
  fcab184 ckpt 477: P1 done: local SDK+mirror, baseline 496 tests green; P2 research in progress (
  4d121cf ckpt 476: Wrote Tj's 06:14Z request into TASKS.md as P1-P5
  a301947 ckpt 475: Released v0.15.6 (code 26), recorded; N1-N6 ticked
  e411094 ckpt 474: pre-release: v0.15.6: every bet you check (widget, CNO tab) is logged in the T
  a5a400c ckpt 473: N6 full tests: review of N1-N5 code + callers, live Novig check (public list h
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
