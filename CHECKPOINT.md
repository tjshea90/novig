# CHECKPOINT 3086 — read me first, then TASKS.md

**Written:** 2026-10-12T14:56:58Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `cb710517c` (this checkpoint is the commit after it)

## Just done
Tj asked for a profit-proven bets/bids toggle (TPF1-5 in TASKS.md). Also: by-hand bet freeing the sheet (placeByHand + pending reservations + sent state in the sheet) is implemented and compiles; its new test hung the run (ApiBettingTest 'a bet by hand frees the sheet'): debug before shipping

## Do this next
1) debug/fix the hanging placeByHand test (check pause lambda / locks), run ApiBettingTest + app tests, ship v0.92.3; 2) TPF1 analysis of research/uploads bet history, then TPF2-5

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  69af95ebf ckpt 3085: pre-release: v0.92.2: timing of the whole bet placement in the event log (t
  273c23a5e ckpt 3084: pre-ship: v0.92.2: timing of the whole bet placement in the event log (the 
  6112e744c ckpt 3083: Bet-sheet lag question (Tj): added timing line to every bet check (BET even
  064babb71 ckpt 3082: pre-release: v0.92.1: fixes slow scans after a reconnect (v0.92.0 carried t
  2c979489e ckpt 3081: pre-ship: v0.92.1: fixes slow scans after a reconnect (v0.92.0 carried the 
  0b6e32333 ckpt 3080: pre-release: v0.92.0: orders over Novig's websocket (trading-key connection
  26748a4b4 ckpt 3079: TNS5 stream history in event log; docs: NOVIG_API.md §27 (orders over the 
  18fee1961 ckpt 3078: TNS4: market status acted on: NovigBook.lifecycle from the stream; Pricing 
  c312ddb79 ckpt 3077: TNS3: socket compression on the market socket (header X-Novig-WS-Compress o
  a412e1130 ckpt 3076: Read Tj's v0.91.0 diagnostics + Test key screenshot (research/novig_v0.91.0
```

(8 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
