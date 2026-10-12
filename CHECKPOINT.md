# CHECKPOINT 3064 — read me first, then TASKS.md

**Written:** 2026-10-12T07:26:22Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `caad78b15` (this checkpoint is the commit after it)

## Just done
TNV2 core: NovigStream per-market channel (bbo|book), deepen/liveTop/room/configureFeed/feedReport on PushedBooks, bbo fallback (named refusal, no snapshot, unreadable, canary), canary via snapshot verb on book, upgrade 429 Retry-After, keepOpen auto-reconnect with backoff; existing stream tests green

## Do this next
Write NovigStreamBboTest (bbo subscribe, partial/complete reads, deepen, pinned=book, fallbacks, canary match/mismatch, toggle, 429 Retry-After, keepOpen reconnect); then NovigPublicClient/NovigSource plumbing (pushedTop, deepen, watchRoom), Scanner deepenEdges, app wiring + settings

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  8e38ddbfd ckpt 3063: TNV1 done: StreamBooks reads both shapes (order/orderId, outcome/outcomeId)
  a47c965cb ckpt 3062: BoltOdds sample #2 read: odds socket WORKS (binary plain-JSON array frames,
  7b918408a ckpt 3061: v0.90.1 released + recorded; Tj asked for Novig bbo + full API use: wrote r
  d3b5a9c1f ckpt 3060: pre-release: v0.90.1: BoltOdds odds socket reads binary frames and subscrib
  2b3323c8e ckpt 3059: pre-ship: v0.90.1: BoltOdds odds socket reads binary frames and subscribes 
  d0a167b80 ckpt 3058: TBP4 done: BoltKeyTest v2 (closes the app's sockets, snapshot of the sport 
  3fa23b9e5 ckpt 3057: TBP1-TBP3 done: BoltOddsStream reads binary frames (BoltFrames utf8/gzip/zl
  189399b0e ckpt 3056: BoltOdds sample #1 read: 34 books (no Pinnacle/Circa/bet365; 9 of Tj's book
  3794ec48b ckpt 3055: v0.90.0 released and recorded: BoltOdds Pro (websocket lines + props, Test 
  a9bc66a3c ckpt 3054: pre-release: v0.90.0: BoltOdds Pro (websocket lines + props, Test key sampl
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
