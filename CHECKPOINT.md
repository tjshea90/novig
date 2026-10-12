# CHECKPOINT 3068 — read me first, then TASKS.md

**Written:** 2026-10-12T07:40:42Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `8836dc8bd` (this checkpoint is the commit after it)

## Just done
TNV8: Test key gains the best-bid check (feed of its own: markets arrive as best bids, the feed's canary vs the book, complete books follow, GIVEN UP named); HealthChecks WARN on a bbo fallback; 5 live-check tests

## Do this next
TNV6 private orders stream: parse orders/positions on the shared socket (OrderFeed), subscribe private, ApiBetPlacer.finish push-first with REST fallback; then full floor + ship v0.91.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/src/main/kotlin/com/tjshea/vigilant/app/HealthChecks.kt

## Last ten checkpoints
```
  1bf9059c7 ckpt 3067: TNV2/TNV5/TNV8 (part): ApiGates.novigBbo/novigKeepOpen (default on) + Setti
  e25fe6c70 ckpt 3066: TNV2/TNV3: NovigSource.pushedTop/deepen/watchRoom, NovigPublicClient (scree
  3788c0ff1 ckpt 3065: TNV2: canary retried once on other markets before bbo is given up, bbo watc
  b5d7268cb ckpt 3064: TNV2 core: NovigStream per-market channel (bbo|book), deepen/liveTop/room/c
  8e38ddbfd ckpt 3063: TNV1 done: StreamBooks reads both shapes (order/orderId, outcome/outcomeId)
  a47c965cb ckpt 3062: BoltOdds sample #2 read: odds socket WORKS (binary plain-JSON array frames,
  7b918408a ckpt 3061: v0.90.1 released + recorded; Tj asked for Novig bbo + full API use: wrote r
  d3b5a9c1f ckpt 3060: pre-release: v0.90.1: BoltOdds odds socket reads binary frames and subscrib
  2b3323c8e ckpt 3059: pre-ship: v0.90.1: BoltOdds odds socket reads binary frames and subscribes 
  d0a167b80 ckpt 3058: TBP4 done: BoltKeyTest v2 (closes the app's sockets, snapshot of the sport 
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
