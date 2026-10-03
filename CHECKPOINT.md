# CHECKPOINT 2444 — read me first, then TASKS.md

**Written:** 2026-10-03T07:43:34Z · **tests:** all 3 fast checks green
**Branch:** `ccr-48b3c757-u0bxg1` · **builds on:** `d8e2170f` (this checkpoint is the commit after it)

## Just done
pre-release: v0.54.0: bids rest to their expiry (no re-post loop), bids that lead their side go first (own bids not counted), bids up count against the wallet, one fills read a pass (no 429s), one bid pass at a time (versionCode 94, v0.54.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.54.0), then run: bash tools/record-release.sh v0.54.0 94 "v0.54.0: bids rest to their expiry (no re-post loop), bids that lead their side go first (own bids not counted), bids up count against the wallet, one fills read a pass (no 429s), one bid pass at a time"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  d8e2170f ckpt 2443: v0.54.0 (code 94) bumped; BRIEF.md v0.54.0 make-orders rules; full floor run
  5b96f57a ckpt 2442: BJ3 done: findings worked/triaged from the v0.53.0 file (lag resolved, 429s,
  9a2850d5 ckpt 2441: NOVIG_API.md §17 verified facts (wallet not held, orders/{id} 404 off book,
  29b46cbc ckpt 2440: BJ2a-f done and ticked: 8 new tests, mutants 7/7
  22a7b37a ckpt 2439: BJ2a-f code in (churn rule, withoutOwn + bidLevels, leaders-first PRIORITY, 
  1331effe ckpt 2438: BJ1 evidence confirmed from Tj's re-sent v0.53.0 file; BJ2 split into BJ2a-f
  1beb897b ckpt 2437: resumed after usage cut: sub-agent workflow results lost (never committed, o
  71b673fb ckpt 2436: BJ1 evidence read (churn loop, wallet over-commit, fills 429s, 41% lead, GC 
  beef13c3 ckpt 2435: BJ: Tj 'None of my auto bids were accepted' (v0.53.0 diag) written to TASKS.
  4d10f75f ckpt 2434: BI done: v0.53.0 (code 93) released + recorded
```
