# CHECKPOINT 2440 — read me first, then TASKS.md

**Written:** 2026-10-03T07:36:37Z · **tests:** all 3 fast checks green
**Branch:** `ccr-48b3c757-u0bxg1` · **builds on:** `84500c9c` (this checkpoint is the commit after it)

## Just done
BJ2a-f done and ticked: 8 new tests, mutants 7/7

## Do this next
BJ2 wrap: NOVIG_API.md §17 (wallet not held, orders/{id} 404 once off book, fills startsAfter), RESEARCH.md §70.9; then BJ3 (GC 64%, 429s on public catalog, other findings)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  22a7b37a ckpt 2439: BJ2a-f code in (churn rule, withoutOwn + bidLevels, leaders-first PRIORITY, 
  1331effe ckpt 2438: BJ1 evidence confirmed from Tj's re-sent v0.53.0 file; BJ2 split into BJ2a-f
  1beb897b ckpt 2437: resumed after usage cut: sub-agent workflow results lost (never committed, o
  71b673fb ckpt 2436: BJ1 evidence read (churn loop, wallet over-commit, fills 429s, 41% lead, GC 
  beef13c3 ckpt 2435: BJ: Tj 'None of my auto bids were accepted' (v0.53.0 diag) written to TASKS.
  4d10f75f ckpt 2434: BI done: v0.53.0 (code 93) released + recorded
  b2a290ad ckpt 2433: pre-release: v0.53.0: bids fully automatic (posted while each scan runs), Bi
  be8b82f4 ckpt 2432: BI1-BI8 done; v0.53.0 (code 93) bumped; tab labels one line
  d478c67b ckpt 2431: test fixes: WalletStripTest own sandbox (like every UI test), CycleRecorderT
  06a89b15 ckpt 2430: BI1/BI3/BI6: background cycle no longer waits for Vigilant's scan (CNO + aut
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
