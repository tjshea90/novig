# CHECKPOINT 3081 — read me first, then TASKS.md

**Written:** 2026-10-12T09:14:14Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `6506d0b42` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.92.1: fixes slow scans after a reconnect (v0.92.0 carried the stream token model across reconnects and reconnected on a compression switch); order socket and compression confirmed working on a live key

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/build.gradle.kts
     M research/lab/NOTES.md
    ?? research/novig_v0.92.0_evidence_2026-10-12.md

## Last ten checkpoints
```
  0b6e32333 ckpt 3080: pre-release: v0.92.0: orders over Novig's websocket (trading-key connection
  26748a4b4 ckpt 3079: TNS5 stream history in event log; docs: NOVIG_API.md §27 (orders over the 
  18fee1961 ckpt 3078: TNS4: market status acted on: NovigBook.lifecycle from the stream; Pricing 
  c312ddb79 ckpt 3077: TNS3: socket compression on the market socket (header X-Novig-WS-Compress o
  a412e1130 ckpt 3076: Read Tj's v0.91.0 diagnostics + Test key screenshot (research/novig_v0.91.0
  14b1640eb ckpt 3075: TNS2 wired: ApiGates.novigSocketOrders, Settings switch + index entry, orde
  8288377fb ckpt 3074: TNS1: NovigOrderSocket (trading key, own connection, place verb, private or
  06a654e4c ckpt 3073: Tj asked: orders over the socket, socket compression, and acting on market 
  379efe3f6 ckpt 3072: pre-release: v0.91.0: Novig best-bid feed (bbo) with a switch back and auto
  a55544e0c ckpt 3071: TNV2 safety nets: bbo given up when 25+ snapshots are all empty, when updat
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
