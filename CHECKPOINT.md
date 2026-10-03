# CHECKPOINT 2411 — read me first, then TASKS.md

**Written:** 2026-10-03T02:39:50Z · **tests:** all 3 fast checks green
**Branch:** `ccr-91942f39-sfdkh9` · **builds on:** `1ac12db7` (this checkpoint is the commit after it)

## Just done
BG6a-d done: client ttl/cancel, maker settings, MakerQuote/MakerPlan/MakerLines, MakerDesk/MakerStore, Tracker.logMakerFills (MakerTest 12, MakerOrdersClientTest 4, mutants 5/5)

## Do this next
BG6e: app MakerRunner (container wiring, background cycle + after scans, fill notifications, cancel on pause/wallet empty), Make tab UI, Diagnostics, Tracker maker tag; load compose skills first

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  49ed7b56 ckpt 2410: BG1-BG5 ticked; BG6 planned in TASKS.md (6a client, 6b settings, 6c MakerQuo
  6bb1779f ckpt 2409: BG1-BG5 research written: NOVIG_API.md §17 (PO + ttl, cancel, queue, fees, 
  aa59075d ckpt 2408: BG1 API read (docs: PO + ttl, GTT, no amend, queue = price then time, cancel
  ce5b1931 ckpt 2407: BG: Tj's make-orders request written to TASKS.md (research BG1-BG5, then pla
  d9acefe2 ckpt 2406: BF answered: Tj sent the RESEARCH.md §69 summary (pro strategies, Vigilant 
  b1d6f76e ckpt 2405: BF done: RESEARCH.md §69 (pro strategies, Vigilant vs pros, Novig maker/tak
  f6753027 ckpt 2404: BF: Tj's deep-research request (pro betting strategies, Vigilant vs pros, No
  064c0204 ckpt 2403: v0.50.0 released, verified, recorded (wallet line on every notification + sl
  62597f20 ckpt 2402: BE1 done: API-Sports + TheRundown researched (RESEARCH.md §68): neither wor
  8ae9e16b ckpt 2401: pre-release: v0.50.0: every Vigilant notification (push or silent) shows the
```

(14 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
