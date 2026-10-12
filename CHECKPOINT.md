# CHECKPOINT 3062 — read me first, then TASKS.md

**Written:** 2026-10-12T07:18:58Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `9c0ec18d0` (this checkpoint is the commit after it)

## Just done
BoltOdds sample #2 read: odds socket WORKS (binary plain-JSON array frames, 55 in 15 s), scores sockets accept subscriptions, ProphetX/Kalshi stay excluded; research/boltodds_sample2_2026-10-12.md, TBP6/TBP7/TBP9/TBP10 updated; StreamBooks rewritten for TNV1 (both shapes, update, bbo, lifecycle; NovigBook.partial) compiles, tests not yet written

## Do this next
TNV1 tests in StreamBooksTest (prose + OpenAPI shapes, update, bbo snapshot/delta/gap, lifecycle string/object), then NovigStream per-channel subscribe (TNV2)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
    ?? research/boltodds_sample2_2026-10-12.md

## Last ten checkpoints
```
  7b918408a ckpt 3061: v0.90.1 released + recorded; Tj asked for Novig bbo + full API use: wrote r
  d3b5a9c1f ckpt 3060: pre-release: v0.90.1: BoltOdds odds socket reads binary frames and subscrib
  2b3323c8e ckpt 3059: pre-ship: v0.90.1: BoltOdds odds socket reads binary frames and subscribes 
  d0a167b80 ckpt 3058: TBP4 done: BoltKeyTest v2 (closes the app's sockets, snapshot of the sport 
  3fa23b9e5 ckpt 3057: TBP1-TBP3 done: BoltOddsStream reads binary frames (BoltFrames utf8/gzip/zl
  189399b0e ckpt 3056: BoltOdds sample #1 read: 34 books (no Pinnacle/Circa/bet365; 9 of Tj's book
  3794ec48b ckpt 3055: v0.90.0 released and recorded: BoltOdds Pro (websocket lines + props, Test 
  a9bc66a3c ckpt 3054: pre-release: v0.90.0: BoltOdds Pro (websocket lines + props, Test key sampl
  d01978332 ckpt 3053: pre-ship: v0.90.0: BoltOdds Pro (websocket lines + props, Test key sample),
  298a71e29 ckpt 3052: TBO: docs written (BOLTODDS_API.md §7, RESEARCH.md §136, CLAUDE.md BoltOd
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
