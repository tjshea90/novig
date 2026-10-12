# CHECKPOINT 3100 — read me first, then TASKS.md

**Written:** 2026-10-12T17:26:45Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `b1021bbdd` (this checkpoint is the commit after it)

## Just done
TST2 done: RepeatGuard (pure, RepeatGuardTest) wired into ApiBetPlacer (auto refused, hand warned), MakerPlan (bids), PinnLiveTrader and LiveBidDesk; settings gates repeatGuard/maxBets/maxDollars/hedge + UI. Tests green for those suites; full floor not yet run.

## Do this next
TST3: build the stream trigger (gates.streamScan): event-driven scan passes from Novig+Bolt pushes limited to free in-memory sources, then auto-bet + bid pass after each; then TST4 live toggle; then full floor, version 0.94.0, ship. v0.93.3 release still pending (CI run 38282919463).

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  2771ed5aa ckpt 3099: Recorded Tj's streaming-engine request as TST1-5; v0.93.3 CI/release still 
  2eb959c28 ckpt 3098: pre-release: v0.93.3: Entain's bwin, Party Sports, Sports Interaction, Ladb
  6dab775a8 ckpt 3097: pre-ship: v0.93.3: Entain's bwin, Party Sports, Sports Interaction, Ladbrok
  7efdb28cc ckpt 3096: pre-release: v0.93.2: a resting bid priced at or over its fair comes down i
  5cb6bc146 ckpt 3095: pre-ship: v0.93.2: a resting bid priced at or over its fair comes down in t
  9621ebb1e ckpt 3094: pre-release: v0.93.1: the bet sheet closes by itself when Novig has the ord
  75311ee0d ckpt 3093: pre-ship: v0.93.1: the bet sheet closes by itself when Novig has the order 
  75fc650d5 ckpt 3092: pre-ship: v0.93.1: the bet sheet closes by itself when Novig has the order 
  17452a28c ckpt 3091: pre-release: v0.93.0: Profit-proven only switch (Settings > Betting & Novig
  6e085878a ckpt 3090: pre-ship: v0.93.0: Profit-proven only switch (Settings > Betting & Novig ac
```

(16 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
