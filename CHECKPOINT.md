# CHECKPOINT 2431 — read me first, then TASKS.md

**Written:** 2026-10-03T05:41:04Z · **tests:** all 3 fast checks green
**Branch:** `ccr-9491e046-7f6pnb` · **builds on:** `6d7f1ae1` (this checkpoint is the commit after it)

## Just done
test fixes: WalletStripTest own sandbox (like every UI test), CycleRecorderTest text; service holds the cycle wake lock until the scan it started ends

## Do this next
full floor 4; ship v0.53.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/src/test/kotlin/com/tjshea/vigilant/app/CycleRecorderTest.kt
     M app/src/test/kotlin/com/tjshea/vigilant/app/WalletStripTest.kt

## Last ten checkpoints
```
  06a89b15 ckpt 2430: BI1/BI3/BI6: background cycle no longer waits for Vigilant's scan (CNO + aut
  0a945718 ckpt 2429: BI1/BI3 work: offline failures not counted + DoH skipped offline; key retrie
  c4671489 ckpt 2428: BI3 in progress: scan on background-priority threads (ScanThreads), CPU spli
  3ec2f412 ckpt 2427: BI2 done: wallet strip above the tab bar (WalletBalance.flow, 30 s refresh o
  7f0b773f ckpt 2426: BI4/BI5/BI8 done: Bids tab always shown with Off/Recommend/Automatic, turnin
  17e09f11 ckpt 2425: BI5 core: maker passes judge running scans (partial: unjudged bids stay), li
  784bead3 ckpt 2424: BI8 added (Tj: Bids tab always shown; turning bids/auto-make on turns on Vig
  58d0876a ckpt 2423: BI: Tj's 6 optimizations (diagnostics, wallet always visible, scan lag, Bids
  9ada3d93 ckpt 2422: BH done: v0.52.0 released + recorded (maker audit fixes, +EV-only invariant,
  4a4a5b6a ckpt 2421: pre-release: v0.52.0: make orders only +EV and never older than their fair (
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
