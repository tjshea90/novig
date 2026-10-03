# CHECKPOINT 2437 — read me first, then TASKS.md

**Written:** 2026-10-03T07:17:23Z · **tests:** all 3 fast checks green
**Branch:** `ccr-48b3c757-u0bxg1` · **builds on:** `f328ce94` (this checkpoint is the commit after it)

## Just done
resumed after usage cut: sub-agent workflow results lost (never committed, other account), diag file absent; BJ1 evidence in TASKS.md is the basis

## Do this next
BJ2: read MakerDesk/MakerQuote/MakerRunner; fix churn, fills reads, wallet budget, lead-first priority; GC

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  71b673fb ckpt 2436: BJ1 evidence read (churn loop, wallet over-commit, fills 429s, 41% lead, GC 
  beef13c3 ckpt 2435: BJ: Tj 'None of my auto bids were accepted' (v0.53.0 diag) written to TASKS.
  4d10f75f ckpt 2434: BI done: v0.53.0 (code 93) released + recorded
  b2a290ad ckpt 2433: pre-release: v0.53.0: bids fully automatic (posted while each scan runs), Bi
  be8b82f4 ckpt 2432: BI1-BI8 done; v0.53.0 (code 93) bumped; tab labels one line
  d478c67b ckpt 2431: test fixes: WalletStripTest own sandbox (like every UI test), CycleRecorderT
  06a89b15 ckpt 2430: BI1/BI3/BI6: background cycle no longer waits for Vigilant's scan (CNO + aut
  0a945718 ckpt 2429: BI1/BI3 work: offline failures not counted + DoH skipped offline; key retrie
  c4671489 ckpt 2428: BI3 in progress: scan on background-priority threads (ScanThreads), CPU spli
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
