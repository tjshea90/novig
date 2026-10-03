# CHECKPOINT 2415 — read me first, then TASKS.md

**Written:** 2026-10-03T02:54:37Z · **tests:** all 3 fast checks green
**Branch:** `ccr-91942f39-sfdkh9` · **builds on:** `56e1fac1` (this checkpoint is the commit after it)

## Just done
pre-release: v0.51.0: make orders (the Bids tab): post-only bids under Vigilant's fair on Novig, re-priced as the fair moves, expiring on their own, fills tracked as bets; research RESEARCH.md §70 (versionCode 91, v0.51.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.51.0), then run: bash tools/record-release.sh v0.51.0 91 "v0.51.0: make orders (the Bids tab): post-only bids under Vigilant's fair on Novig, re-priced as the fair moves, expiring on their own, fills tracked as bets; research RESEARCH.md §70"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  56e1fac1 ckpt 2414: pre-ship: v0.51.0: make orders (the Bids tab): post-only bids under Vigilant
  e79c9c9b ckpt 2413: BG6e done: Bids tab + MakerRunner + container wiring + Diagnostics + Tracker
  6e894597 ckpt 2412: BG6e in progress: MakerRunner (container: desk, after-scan pass, cancel on o
  9fa8c34e ckpt 2411: BG6a-d done: client ttl/cancel, maker settings, MakerQuote/MakerPlan/MakerLi
  49ed7b56 ckpt 2410: BG1-BG5 ticked; BG6 planned in TASKS.md (6a client, 6b settings, 6c MakerQuo
  6bb1779f ckpt 2409: BG1-BG5 research written: NOVIG_API.md §17 (PO + ttl, cancel, queue, fees, 
  aa59075d ckpt 2408: BG1 API read (docs: PO + ttl, GTT, no amend, queue = price then time, cancel
  ce5b1931 ckpt 2407: BG: Tj's make-orders request written to TASKS.md (research BG1-BG5, then pla
  d9acefe2 ckpt 2406: BF answered: Tj sent the RESEARCH.md §69 summary (pro strategies, Vigilant 
  b1d6f76e ckpt 2405: BF done: RESEARCH.md §69 (pro strategies, Vigilant vs pros, Novig maker/tak
```
