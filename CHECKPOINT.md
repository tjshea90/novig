# CHECKPOINT 2436 — read me first, then TASKS.md

**Written:** 2026-10-03T06:42:22Z · **tests:** all 3 fast checks green
**Branch:** `ccr-9491e046-7f6pnb` · **builds on:** `38c20230` (this checkpoint is the commit after it)

## Just done
BJ1 evidence read (churn loop, wallet over-commit, fills 429s, 41% lead, GC 64%); workflow wf_3002be9a-7af diagnosing

## Do this next
wait for workflow; implement verified fixes (churn, fills reads, wallet budget, lead-first priority, GC); tests; ship

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  beef13c3 ckpt 2435: BJ: Tj 'None of my auto bids were accepted' (v0.53.0 diag) written to TASKS.
  4d10f75f ckpt 2434: BI done: v0.53.0 (code 93) released + recorded
  b2a290ad ckpt 2433: pre-release: v0.53.0: bids fully automatic (posted while each scan runs), Bi
  be8b82f4 ckpt 2432: BI1-BI8 done; v0.53.0 (code 93) bumped; tab labels one line
  d478c67b ckpt 2431: test fixes: WalletStripTest own sandbox (like every UI test), CycleRecorderT
  06a89b15 ckpt 2430: BI1/BI3/BI6: background cycle no longer waits for Vigilant's scan (CNO + aut
  0a945718 ckpt 2429: BI1/BI3 work: offline failures not counted + DoH skipped offline; key retrie
  c4671489 ckpt 2428: BI3 in progress: scan on background-priority threads (ScanThreads), CPU spli
  3ec2f412 ckpt 2427: BI2 done: wallet strip above the tab bar (WalletBalance.flow, 30 s refresh o
  7f0b773f ckpt 2426: BI4/BI5/BI8 done: Bids tab always shown with Off/Recommend/Automatic, turnin
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
