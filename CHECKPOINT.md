# CHECKPOINT 2428 — read me first, then TASKS.md

**Written:** 2026-10-03T05:18:37Z · **tests:** all 3 fast checks green
**Branch:** `ccr-9491e046-7f6pnb` · **builds on:** `9e869284` (this checkpoint is the commit after it)

## Just done
BI3 in progress: scan on background-priority threads (ScanThreads), CPU split per scan in Diagnostics (ThreadCpu), auto-scan service samples progress 1/s, maker book devigs only after precheck; tests green

## Do this next
full floor running; then BI1 diag findings (DoH offline false FAILURE, Novig public 429s, WTA matching, Parlay 503 props)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  3ec2f412 ckpt 2427: BI2 done: wallet strip above the tab bar (WalletBalance.flow, 30 s refresh o
  7f0b773f ckpt 2426: BI4/BI5/BI8 done: Bids tab always shown with Off/Recommend/Automatic, turnin
  17e09f11 ckpt 2425: BI5 core: maker passes judge running scans (partial: unjudged bids stay), li
  784bead3 ckpt 2424: BI8 added (Tj: Bids tab always shown; turning bids/auto-make on turns on Vig
  58d0876a ckpt 2423: BI: Tj's 6 optimizations (diagnostics, wallet always visible, scan lag, Bids
  9ada3d93 ckpt 2422: BH done: v0.52.0 released + recorded (maker audit fixes, +EV-only invariant,
  4a4a5b6a ckpt 2421: pre-release: v0.52.0: make orders only +EV and never older than their fair (
  2e6790e6 ckpt 2420: pre-ship: v0.52.0: make orders only +EV and never older than their fair (exp
  46b57f5f ckpt 2419: BH1-BH6 done: maker audit fixes (6 bugs), books agree + sharp veto + Kelly, 
  597acd33 ckpt 2418: BH1-3 data fixes: cancels confirmed (CANCELING), fills read even on 404 / lo
```

(8 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
