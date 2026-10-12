# CHECKPOINT 3070 — read me first, then TASKS.md

**Written:** 2026-10-12T07:54:11Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `c8f9fc931` (this checkpoint is the commit after it)

## Just done
TNV: docs (NOVIG_API.md §26, CLAUDE.md standing rule), tune raises the room to the key's cap, tests for the two regressions (BiggerScans fake, BurstUiTest source check) fixed; full floor was green but for those two

## Do this next
Re-run the floor, bump versionCode 223 / 0.91.0 (BUILDLOG), ship.sh, CI wait via send_later, release, tell Tj what to tap (Settings > Betting & Novig account > Test key) and what to send back

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M CLAUDE.md
     M NOVIG_API.md

## Last ten checkpoints
```
  1dda5d441 ckpt 3069: TNV6: private orders stream: OrderEvents (open/fill/cancel/reject, seq gaps
  544ebf656 ckpt 3068: TNV8: Test key gains the best-bid check (feed of its own: markets arrive as
  1bf9059c7 ckpt 3067: TNV2/TNV5/TNV8 (part): ApiGates.novigBbo/novigKeepOpen (default on) + Setti
  e25fe6c70 ckpt 3066: TNV2/TNV3: NovigSource.pushedTop/deepen/watchRoom, NovigPublicClient (scree
  3788c0ff1 ckpt 3065: TNV2: canary retried once on other markets before bbo is given up, bbo watc
  b5d7268cb ckpt 3064: TNV2 core: NovigStream per-market channel (bbo|book), deepen/liveTop/room/c
  8e38ddbfd ckpt 3063: TNV1 done: StreamBooks reads both shapes (order/orderId, outcome/outcomeId)
  a47c965cb ckpt 3062: BoltOdds sample #2 read: odds socket WORKS (binary plain-JSON array frames,
  7b918408a ckpt 3061: v0.90.1 released + recorded; Tj asked for Novig bbo + full API use: wrote r
  d3b5a9c1f ckpt 3060: pre-release: v0.90.1: BoltOdds odds socket reads binary frames and subscrib
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
