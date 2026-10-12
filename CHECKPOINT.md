# CHECKPOINT 3104 — read me first, then TASKS.md

**Written:** 2026-10-12T17:41:04Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `8a2711b77` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.94.0: streaming engine (Novig and BoltOdds pushes start a scan pass at once, free sources only; switch in Settings, off by default; live games toggle uses the existing Pinnodds live lane and live bids)

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/src/main/kotlin/com/tjshea/vigilant/app/MainActivity.kt
     M app/src/test/kotlin/com/tjshea/vigilant/app/AutoScanTest.kt

## Last ten checkpoints
```
  8a2711b77 ckpt 3103: pre-ship: v0.94.0: streaming engine (Novig and BoltOdds pushes start a scan
  9b66c0e71 ckpt 3102: pre-release: v0.93.4: repeat and hedge guard (a side is bet or bid at most 
  21712abeb ckpt 3101: pre-ship: v0.93.4: repeat and hedge guard (a side is bet or bid at most twi
  9c78c9d5f ckpt 3100: TST2 done: RepeatGuard (pure, RepeatGuardTest) wired into ApiBetPlacer (aut
  2771ed5aa ckpt 3099: Recorded Tj's streaming-engine request as TST1-5; v0.93.3 CI/release still 
  2eb959c28 ckpt 3098: pre-release: v0.93.3: Entain's bwin, Party Sports, Sports Interaction, Ladb
  6dab775a8 ckpt 3097: pre-ship: v0.93.3: Entain's bwin, Party Sports, Sports Interaction, Ladbrok
  7efdb28cc ckpt 3096: pre-release: v0.93.2: a resting bid priced at or over its fair comes down i
  5cb6bc146 ckpt 3095: pre-ship: v0.93.2: a resting bid priced at or over its fair comes down in t
  9621ebb1e ckpt 3094: pre-release: v0.93.1: the bet sheet closes by itself when Novig has the ord
```
