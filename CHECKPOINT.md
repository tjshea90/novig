# CHECKPOINT 3078 — read me first, then TASKS.md

**Written:** 2026-10-12T08:41:38Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `a5f5e0e7a` (this checkpoint is the commit after it)

## Just done
TNS4: market status acted on: NovigBook.lifecycle from the stream; Pricing leaves out CLOSED and in-play markets; planner refuses a market the feed says is CLOSED and treats GOLIVE as live (auto-bet refuses, by hand priced with the live fee); 3 tests mutation-checked

## Do this next
TNS5 stream history in event log + feed report wording, then docs NOVIG_API.md §27, CLAUDE.md rule, full floor, ship v0.92.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  c312ddb79 ckpt 3077: TNS3: socket compression on the market socket (header X-Novig-WS-Compress o
  a412e1130 ckpt 3076: Read Tj's v0.91.0 diagnostics + Test key screenshot (research/novig_v0.91.0
  14b1640eb ckpt 3075: TNS2 wired: ApiGates.novigSocketOrders, Settings switch + index entry, orde
  8288377fb ckpt 3074: TNS1: NovigOrderSocket (trading key, own connection, place verb, private or
  06a654e4c ckpt 3073: Tj asked: orders over the socket, socket compression, and acting on market 
  379efe3f6 ckpt 3072: pre-release: v0.91.0: Novig best-bid feed (bbo) with a switch back and auto
  a55544e0c ckpt 3071: TNV2 safety nets: bbo given up when 25+ snapshots are all empty, when updat
  0ebec61a3 ckpt 3070: TNV: docs (NOVIG_API.md §26, CLAUDE.md standing rule), tune raises the roo
  1dda5d441 ckpt 3069: TNV6: private orders stream: OrderEvents (open/fill/cancel/reject, seq gaps
  544ebf656 ckpt 3068: TNV8: Test key gains the best-bid check (feed of its own: markets arrive as
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
