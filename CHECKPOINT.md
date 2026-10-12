# CHECKPOINT 3074 — read me first, then TASKS.md

**Written:** 2026-10-12T08:20:07Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `eaccb3836` (this checkpoint is the commit after it)

## Just done
TNS1: NovigOrderSocket (trading key, own connection, place verb, private orders, error-code to status table, never replays, REST once on a frame Novig could not read, on-hold after a lost/unreadable answer) + NovigTradingClient.placeOrder uses it for IOC/FOK; 13 mock tests green

## Do this next
TNS2 wiring: ApiGates.novigSocketOrders, AppContainer builds the socket with the trading signer in useConnection, warm on plan/auto-bet, placer events prefer the socket's, Diagnostics/health, Settings switch

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  06a654e4c ckpt 3073: Tj asked: orders over the socket, socket compression, and acting on market 
  379efe3f6 ckpt 3072: pre-release: v0.91.0: Novig best-bid feed (bbo) with a switch back and auto
  a55544e0c ckpt 3071: TNV2 safety nets: bbo given up when 25+ snapshots are all empty, when updat
  0ebec61a3 ckpt 3070: TNV: docs (NOVIG_API.md §26, CLAUDE.md standing rule), tune raises the roo
  1dda5d441 ckpt 3069: TNV6: private orders stream: OrderEvents (open/fill/cancel/reject, seq gaps
  544ebf656 ckpt 3068: TNV8: Test key gains the best-bid check (feed of its own: markets arrive as
  1bf9059c7 ckpt 3067: TNV2/TNV5/TNV8 (part): ApiGates.novigBbo/novigKeepOpen (default on) + Setti
  e25fe6c70 ckpt 3066: TNV2/TNV3: NovigSource.pushedTop/deepen/watchRoom, NovigPublicClient (scree
  3788c0ff1 ckpt 3065: TNV2: canary retried once on other markets before bbo is given up, bbo watc
  b5d7268cb ckpt 3064: TNV2 core: NovigStream per-market channel (bbo|book), deepen/liveTop/room/c
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
