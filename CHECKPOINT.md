# CHECKPOINT 3936 — read me first, then TASKS.md

**Written:** 2026-10-21T23:47:18Z · **tests:** all 9 fast checks green
**Branch:** `claude/new-session-51eqix` · **builds on:** `b38c066e2` (this checkpoint is the commit after it)

## Just done
pre-release: v1.0.43: SharpAPI WebSocket stream (odds and game state pushed to the engine; REST delta becomes a 60 s reconcile) and live game state as a live-lane score source; socket switch in Settings > APIs (versionCode 383, v1.0.43)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.43), then run: bash tools/record-release.sh v1.0.43 383 "v1.0.43: SharpAPI WebSocket stream (odds and game state pushed to the engine; REST delta becomes a 60 s reconcile) and live game state as a live-lane score source; socket switch in Settings > APIs"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  cc2080cb6 ckpt 3935: SA done: v1.0.42 shipped (release building); SharpAPI on engine (store + de
  01bc3ee0f ckpt 3934: pre-release: v1.0.42: SharpAPI Sharp plan: the engine keeps a copy of 25 bo
  ab65f67cf ckpt 3933: SA4a: SharpApiLiveFeed (Pinnacle live from the store as Pinnodds frames, 1 
  fbb5059fc ckpt 3931: ENGINEER 2026-10-21: watched engine, volume loop recorded, XOW6 added, no c
  340405fb6 ckpt 3932: SA2/SA3 tested: SharpApiTest (6: lines, pairs, excluded books, live split, 
  3e280d555 ckpt 3931: SA2/SA3 code: SharpApiClient (900/min, 4 in flight, 401/429 handling, usage
  41c3fa5ea ckpt 3930: SA1: SharpAPI docs saved (39 pages + llms-full) and SHARPAPI.md written
  2ccee2a88 ckpt 3929: v1.0.41 recorded; engine verified: CNO answers the runner (100 rows, good r
  f88f264b1 ckpt 3928: TR: four trial APIs judged (research/api_trials_2026-10-21.md): SharpAPI wo
  a7b61ccd6 ckpt 3927: pre-release: v1.0.41: full test fixes: Kalshi CNO rows open at Kalshi every
```

(13 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
