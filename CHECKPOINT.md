# CHECKPOINT 3093 — read me first, then TASKS.md

**Written:** 2026-10-12T15:58:39Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `75fc650d5` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.93.1: the bet sheet closes by itself when Novig has the order and the result comes as a notification (placed or the error); a bet by hand no longer waits behind the bid desk for the order lock (the lag), lock wait timed in the BET event

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M app/src/main/kotlin/com/tjshea/vigilant/app/ApiBetting.kt
     M app/src/main/kotlin/com/tjshea/vigilant/app/VigilantApp.kt
     M app/src/test/kotlin/com/tjshea/vigilant/app/AutoBettorTest.kt

## Last ten checkpoints
```
  75fc650d5 ckpt 3092: pre-ship: v0.93.1: the bet sheet closes by itself when Novig has the order 
  17452a28c ckpt 3091: pre-release: v0.93.0: Profit-proven only switch (Settings > Betting & Novig
  6e085878a ckpt 3090: pre-ship: v0.93.0: Profit-proven only switch (Settings > Betting & Novig ac
  a41a7c859 ckpt 3089: TPF1-4: profit-by-type analysis (research/profit_types_2026-10-12.md), Prof
  68c3a2724 ckpt 3088: pre-release: v0.92.3: a bet by hand frees the bet sheet once Novig has the 
  9040b4ca7 ckpt 3087: pre-ship: v0.92.3: a bet by hand frees the bet sheet once Novig has the ord
  4eb4e8141 ckpt 3086: Tj asked for a profit-proven bets/bids toggle (TPF1-5 in TASKS.md). Also: b
  69af95ebf ckpt 3085: pre-release: v0.92.2: timing of the whole bet placement in the event log (t
  273c23a5e ckpt 3084: pre-ship: v0.92.2: timing of the whole bet placement in the event log (the 
  6112e744c ckpt 3083: Bet-sheet lag question (Tj): added timing line to every bet check (BET even
```
