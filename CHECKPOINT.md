# CHECKPOINT 2417 — read me first, then TASKS.md

**Written:** 2026-10-03T03:06:41Z · **tests:** all 3 fast checks green
**Branch:** `ccr-91942f39-sfdkh9` · **builds on:** `c25c8790` (this checkpoint is the commit after it)

## Just done
BH0 done: v0.51.0 released + recorded (BG6 ticked)

## Do this next
BH1-3: fix audit findings in MakerDesk (cancel is only queued: CANCELING until Novig confirms; replacement only after; fills on 404; lost-answer search by clientId across statuses; refused cool-off), MakerQuote (expiry bounded by fair freshness and the stop window, fair age required, game lines need sharp, books agreeing, sharp veto)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  622feada ckpt 2416: BH: Tj's make-orders follow-up written to TASKS.md (full tests, +EV only, ti
  94cdbdc2 ckpt 2415: pre-release: v0.51.0: make orders (the Bids tab): post-only bids under Vigil
  56e1fac1 ckpt 2414: pre-ship: v0.51.0: make orders (the Bids tab): post-only bids under Vigilant
  e79c9c9b ckpt 2413: BG6e done: Bids tab + MakerRunner + container wiring + Diagnostics + Tracker
  6e894597 ckpt 2412: BG6e in progress: MakerRunner (container: desk, after-scan pass, cancel on o
  9fa8c34e ckpt 2411: BG6a-d done: client ttl/cancel, maker settings, MakerQuote/MakerPlan/MakerLi
  49ed7b56 ckpt 2410: BG1-BG5 ticked; BG6 planned in TASKS.md (6a client, 6b settings, 6c MakerQuo
  6bb1779f ckpt 2409: BG1-BG5 research written: NOVIG_API.md §17 (PO + ttl, cancel, queue, fees, 
  aa59075d ckpt 2408: BG1 API read (docs: PO + ttl, GTT, no amend, queue = price then time, cancel
  ce5b1931 ckpt 2407: BG: Tj's make-orders request written to TASKS.md (research BG1-BG5, then pla
```
