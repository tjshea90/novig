# CHECKPOINT 3067 — read me first, then TASKS.md

**Written:** 2026-10-12T07:38:32Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `3a2800800` (this checkpoint is the commit after it)

## Just done
TNV2/TNV5/TNV8 (part): ApiGates.novigBbo/novigKeepOpen (default on) + Settings switches + index; configureFeed from syncSgo/useConnection; Scanner deepExtra = markets with Vigilant bids; burst recorder on the shared stream (NovigStream.pin per owner, union); Diagnostics 'Novig live feed' lines; 38 stream/scan tests green

## Do this next
Test key: compare bbo with book on real markets (NovigLiveCheck v2); HealthChecks entry for a bbo fallback; then TNV6 private orders stream (OrderFeed, finish() push-first)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  e25fe6c70 ckpt 3066: TNV2/TNV3: NovigSource.pushedTop/deepen/watchRoom, NovigPublicClient (scree
  3788c0ff1 ckpt 3065: TNV2: canary retried once on other markets before bbo is given up, bbo watc
  b5d7268cb ckpt 3064: TNV2 core: NovigStream per-market channel (bbo|book), deepen/liveTop/room/c
  8e38ddbfd ckpt 3063: TNV1 done: StreamBooks reads both shapes (order/orderId, outcome/outcomeId)
  a47c965cb ckpt 3062: BoltOdds sample #2 read: odds socket WORKS (binary plain-JSON array frames,
  7b918408a ckpt 3061: v0.90.1 released + recorded; Tj asked for Novig bbo + full API use: wrote r
  d3b5a9c1f ckpt 3060: pre-release: v0.90.1: BoltOdds odds socket reads binary frames and subscrib
  2b3323c8e ckpt 3059: pre-ship: v0.90.1: BoltOdds odds socket reads binary frames and subscribes 
  d0a167b80 ckpt 3058: TBP4 done: BoltKeyTest v2 (closes the app's sockets, snapshot of the sport 
  3fa23b9e5 ckpt 3057: TBP1-TBP3 done: BoltOddsStream reads binary frames (BoltFrames utf8/gzip/zl
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
