# CHECKPOINT 2441 — read me first, then TASKS.md

**Written:** 2026-10-03T07:37:38Z · **tests:** all 3 fast checks green
**Branch:** `ccr-48b3c757-u0bxg1` · **builds on:** `4c222bb7` (this checkpoint is the commit after it)

## Just done
NOVIG_API.md §17 verified facts (wallet not held, orders/{id} 404 off book, fills startsAfter batch) + RESEARCH.md §70.9 why no auto bid filled

## Do this next
BJ3: GC 64% during scans + public catalog 429s + other findings; then BJ4 sweep/floor/ship

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M RESEARCH.md

## Last ten checkpoints
```
  29b46cbc ckpt 2440: BJ2a-f done and ticked: 8 new tests, mutants 7/7
  22a7b37a ckpt 2439: BJ2a-f code in (churn rule, withoutOwn + bidLevels, leaders-first PRIORITY, 
  1331effe ckpt 2438: BJ1 evidence confirmed from Tj's re-sent v0.53.0 file; BJ2 split into BJ2a-f
  1beb897b ckpt 2437: resumed after usage cut: sub-agent workflow results lost (never committed, o
  71b673fb ckpt 2436: BJ1 evidence read (churn loop, wallet over-commit, fills 429s, 41% lead, GC 
  beef13c3 ckpt 2435: BJ: Tj 'None of my auto bids were accepted' (v0.53.0 diag) written to TASKS.
  4d10f75f ckpt 2434: BI done: v0.53.0 (code 93) released + recorded
  b2a290ad ckpt 2433: pre-release: v0.53.0: bids fully automatic (posted while each scan runs), Bi
  be8b82f4 ckpt 2432: BI1-BI8 done; v0.53.0 (code 93) bumped; tab labels one line
  d478c67b ckpt 2431: test fixes: WalletStripTest own sandbox (like every UI test), CycleRecorderT
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
