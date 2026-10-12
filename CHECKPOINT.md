# CHECKPOINT 3077 — read me first, then TASKS.md

**Written:** 2026-10-12T08:37:02Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `d5d065400` (this checkpoint is the commit after it)

## Just done
TNS3: socket compression on the market socket (header X-Novig-WS-Compress only when asked, binary raw-DEFLATE read like text, plain-JSON-in-binary tolerated, 3 unreadable frames in a row give it up and reconnect plain, byte counters in Diagnostics, ApiGates.novigCompress + Settings switch + index); 5 mock tests

## Do this next
TNS4 market status (lifecycle) acted on in Pricing/placer/Feed; TNS5 stream history in event log; TNS6 docs NOVIG_API.md §27 + ship v0.92.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M data/src/test/kotlin/com/tjshea/vigilant/data/novig/stream/NovigStreamCompressTest.kt

## Last ten checkpoints
```
  a412e1130 ckpt 3076: Read Tj's v0.91.0 diagnostics + Test key screenshot (research/novig_v0.91.0
  14b1640eb ckpt 3075: TNS2 wired: ApiGates.novigSocketOrders, Settings switch + index entry, orde
  8288377fb ckpt 3074: TNS1: NovigOrderSocket (trading key, own connection, place verb, private or
  06a654e4c ckpt 3073: Tj asked: orders over the socket, socket compression, and acting on market 
  379efe3f6 ckpt 3072: pre-release: v0.91.0: Novig best-bid feed (bbo) with a switch back and auto
  a55544e0c ckpt 3071: TNV2 safety nets: bbo given up when 25+ snapshots are all empty, when updat
  0ebec61a3 ckpt 3070: TNV: docs (NOVIG_API.md §26, CLAUDE.md standing rule), tune raises the roo
  1dda5d441 ckpt 3069: TNV6: private orders stream: OrderEvents (open/fill/cancel/reject, seq gaps
  544ebf656 ckpt 3068: TNV8: Test key gains the best-bid check (feed of its own: markets arrive as
  1bf9059c7 ckpt 3067: TNV2/TNV5/TNV8 (part): ApiGates.novigBbo/novigKeepOpen (default on) + Setti
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
