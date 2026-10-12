# CHECKPOINT 3057 — read me first, then TASKS.md

**Written:** 2026-10-12T06:48:31Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `eeec3498c` (this checkpoint is the commit after it)

## Just done
TBP1-TBP3 done: BoltOddsStream reads binary frames (BoltFrames utf8/gzip/zlib), subscribes after 4s without greeting, LIVE on any frame, aliveAt needs a frame; fixed double-subscribe race (subLock); 'pre' sport suffix; BoltOdds market aliases + real-names test; 46 Bolt tests green

## Do this next
TBP4: BoltKeyTest v2 (sport with markets, several books incl prophetx/kalshi/novig, close app lanes first, livescores socket probe, report raw frames/openInfo in BoltOddsFeed.report + probe section); then TBP5 full floor + ship v0.90.1 (code 222) + release + ask Tj to re-run Test

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  189399b0e ckpt 3056: BoltOdds sample #1 read: 34 books (no Pinnacle/Circa/bet365; 9 of Tj's book
  3794ec48b ckpt 3055: v0.90.0 released and recorded: BoltOdds Pro (websocket lines + props, Test 
  a9bc66a3c ckpt 3054: pre-release: v0.90.0: BoltOdds Pro (websocket lines + props, Test key sampl
  d01978332 ckpt 3053: pre-ship: v0.90.0: BoltOdds Pro (websocket lines + props, Test key sample),
  298a71e29 ckpt 3052: TBO: docs written (BOLTODDS_API.md §7, RESEARCH.md §136, CLAUDE.md BoltOd
  e568abb49 ckpt 3051: TBO3 (app wiring): ApiEntry.BOLTODDS, VigilantApp client/feed/sources/boltA
  855ff31d5 ckpt 3050: TBO3 (data side, part 1): data/.../boltodds/ Models, Parser, Markets, Books
  9bd2933e8 ckpt 3049: TBO2 done: Novig docs re-read (135 pages + OpenAPI), findings in research/n
  678d21dbb ckpt 3046: TBO1: BoltOdds docs read in full (single page), saved raw to research/bolto
  a3856d33a ckpt 3045: TBO: wrote Tj's BoltOdds Pro + Novig docs request into TASKS.md (TBO1-TBO5)
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
