# CHECKPOINT 3066 — read me first, then TASKS.md

**Written:** 2026-10-12T07:33:08Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `a52b84f39` (this checkpoint is the commit after it)

## Just done
TNV2/TNV3: NovigSource.pushedTop/deepen/watchRoom, NovigPublicClient (screening context, never caches partial books), Scanner.deepenEdges (+EV markets moved to the full book and priced again, REST for the rest), dynamic stream room; bbo scan result == all-book result (BboScanTest); 15+3 tests green

## Do this next
App wiring: ApiGates.novigBbo/novigKeepOpen (+ArgSlot test), VigilantApp configureFeed on settings + stream report in Diagnostics, deepen markets with resting bids, burst recorder onto the shared stream, Settings UI + Test key (bbo vs book on real markets)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M data/src/test/kotlin/com/tjshea/vigilant/data/scanner/BboScanTest.kt

## Last ten checkpoints
```
  3788c0ff1 ckpt 3065: TNV2: canary retried once on other markets before bbo is given up, bbo watc
  b5d7268cb ckpt 3064: TNV2 core: NovigStream per-market channel (bbo|book), deepen/liveTop/room/c
  8e38ddbfd ckpt 3063: TNV1 done: StreamBooks reads both shapes (order/orderId, outcome/outcomeId)
  a47c965cb ckpt 3062: BoltOdds sample #2 read: odds socket WORKS (binary plain-JSON array frames,
  7b918408a ckpt 3061: v0.90.1 released + recorded; Tj asked for Novig bbo + full API use: wrote r
  d3b5a9c1f ckpt 3060: pre-release: v0.90.1: BoltOdds odds socket reads binary frames and subscrib
  2b3323c8e ckpt 3059: pre-ship: v0.90.1: BoltOdds odds socket reads binary frames and subscribes 
  d0a167b80 ckpt 3058: TBP4 done: BoltKeyTest v2 (closes the app's sockets, snapshot of the sport 
  3fa23b9e5 ckpt 3057: TBP1-TBP3 done: BoltOddsStream reads binary frames (BoltFrames utf8/gzip/zl
  189399b0e ckpt 3056: BoltOdds sample #1 read: 34 books (no Pinnacle/Circa/bet365; 9 of Tj's book
```

(8 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
