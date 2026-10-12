# CHECKPOINT 3084 — read me first, then TASKS.md

**Written:** 2026-10-12T09:38:17Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `2969c9c1b` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.92.2: timing of the whole bet placement in the event log (the sheet's price check is 90-160 ms, so the lag is after the confirm tap)

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/build.gradle.kts
     M research/novig_v0.92.0_evidence_2026-10-12.md

## Last ten checkpoints
```
  6112e744c ckpt 3083: Bet-sheet lag question (Tj): added timing line to every bet check (BET even
  064babb71 ckpt 3082: pre-release: v0.92.1: fixes slow scans after a reconnect (v0.92.0 carried t
  2c979489e ckpt 3081: pre-ship: v0.92.1: fixes slow scans after a reconnect (v0.92.0 carried the 
  0b6e32333 ckpt 3080: pre-release: v0.92.0: orders over Novig's websocket (trading-key connection
  26748a4b4 ckpt 3079: TNS5 stream history in event log; docs: NOVIG_API.md §27 (orders over the 
  18fee1961 ckpt 3078: TNS4: market status acted on: NovigBook.lifecycle from the stream; Pricing 
  c312ddb79 ckpt 3077: TNS3: socket compression on the market socket (header X-Novig-WS-Compress o
  a412e1130 ckpt 3076: Read Tj's v0.91.0 diagnostics + Test key screenshot (research/novig_v0.91.0
  14b1640eb ckpt 3075: TNS2 wired: ApiGates.novigSocketOrders, Settings switch + index entry, orde
  8288377fb ckpt 3074: TNS1: NovigOrderSocket (trading key, own connection, place verb, private or
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
