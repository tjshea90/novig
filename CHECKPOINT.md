# CHECKPOINT 482 — read me first, then TASKS.md

**Written:** 2026-09-27T06:42:18Z · **tests:** all 1 fast checks green
**Branch:** `claude/vigilant-testing-odds-apis-mpvkc7` · **builds on:** `b547d25` (this checkpoint is the commit after it)

## Just done
P3 done: PinnWire (Pinnacle props) + PropLine sources wired end to end; full floor 512 tests, 0 failures

## Do this next
P4 full sweep: data/novig (stream unused? check), reference Kalshi/Polymarket, match, scanner ScanRunner, cno/*, tracker/*, teams, store; app VM/service/widget/screens. Candidate fix found: NovigPublicClient.paged() has no retry on a short 429 (board fetch fails the whole scan)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  ea98af9 ckpt 481: P3c app wiring: UiState pinnwire/propline keys + keysOf/withKeys, AppContainer
  9dc7beb ckpt 480: P3a+P3b data layer: PinnWire Pinnacle props + PropLine client/props source, Sc
  14e3f27 ckpt 479: P3a data: PinnapiClient hosts (PinnWire first, x-api-key, include_specials whe
  73c8699 ckpt 478: P2 research written: RESEARCH.md §22 (PinnWire free Pinnacle props measured l
  fcab184 ckpt 477: P1 done: local SDK+mirror, baseline 496 tests green; P2 research in progress (
  4d121cf ckpt 476: Wrote Tj's 06:14Z request into TASKS.md as P1-P5
  a301947 ckpt 475: Released v0.15.6 (code 26), recorded; N1-N6 ticked
  e411094 ckpt 474: pre-release: v0.15.6: every bet you check (widget, CNO tab) is logged in the T
  a5a400c ckpt 473: N6 full tests: review of N1-N5 code + callers, live Novig check (public list h
  426cb05 ckpt 472: N1-N5 done and ticked (TASKS.md names tests); one-time import flag (tracker_im
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
