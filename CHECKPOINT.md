# CHECKPOINT 3073 — read me first, then TASKS.md

**Written:** 2026-10-12T08:15:51Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `3bddfd91b` (this checkpoint is the commit after it)

## Just done
Tj asked: orders over the socket, socket compression, and acting on market status (explain + build if it helps): wrote TASKS TNS1-TNS7 with the design (place needs the trading key: own order socket; ack/error shapes from the OpenAPI)

## Do this next
TNS1: NovigOrderSocket + NovigTradingClient.placeOrder via socket (IOC/FOK) with REST fallback + mock tests

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  379efe3f6 ckpt 3072: pre-release: v0.91.0: Novig best-bid feed (bbo) with a switch back and auto
  a55544e0c ckpt 3071: TNV2 safety nets: bbo given up when 25+ snapshots are all empty, when updat
  0ebec61a3 ckpt 3070: TNV: docs (NOVIG_API.md §26, CLAUDE.md standing rule), tune raises the roo
  1dda5d441 ckpt 3069: TNV6: private orders stream: OrderEvents (open/fill/cancel/reject, seq gaps
  544ebf656 ckpt 3068: TNV8: Test key gains the best-bid check (feed of its own: markets arrive as
  1bf9059c7 ckpt 3067: TNV2/TNV5/TNV8 (part): ApiGates.novigBbo/novigKeepOpen (default on) + Setti
  e25fe6c70 ckpt 3066: TNV2/TNV3: NovigSource.pushedTop/deepen/watchRoom, NovigPublicClient (scree
  3788c0ff1 ckpt 3065: TNV2: canary retried once on other markets before bbo is given up, bbo watc
  b5d7268cb ckpt 3064: TNV2 core: NovigStream per-market channel (bbo|book), deepen/liveTop/room/c
  8e38ddbfd ckpt 3063: TNV1 done: StreamBooks reads both shapes (order/orderId, outcome/outcomeId)
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
