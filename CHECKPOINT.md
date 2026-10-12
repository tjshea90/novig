# CHECKPOINT 3094 — read me first, then TASKS.md

**Written:** 2026-10-12T16:00:41Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `75311ee0d` (this checkpoint is the commit after it)

## Just done
pre-release: v0.93.1: the bet sheet closes by itself when Novig has the order and the result comes as a notification (placed or the error); a bet by hand no longer waits behind the bid desk for the order lock (the lag), lock wait timed in the BET event (versionCode 229, v0.93.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.93.1), then run: bash tools/record-release.sh v0.93.1 229 "v0.93.1: the bet sheet closes by itself when Novig has the order and the result comes as a notification (placed or the error); a bet by hand no longer waits behind the bid desk for the order lock (the lag), lock wait timed in the BET event"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  75311ee0d ckpt 3093: pre-ship: v0.93.1: the bet sheet closes by itself when Novig has the order 
  75fc650d5 ckpt 3092: pre-ship: v0.93.1: the bet sheet closes by itself when Novig has the order 
  17452a28c ckpt 3091: pre-release: v0.93.0: Profit-proven only switch (Settings > Betting & Novig
  6e085878a ckpt 3090: pre-ship: v0.93.0: Profit-proven only switch (Settings > Betting & Novig ac
  a41a7c859 ckpt 3089: TPF1-4: profit-by-type analysis (research/profit_types_2026-10-12.md), Prof
  68c3a2724 ckpt 3088: pre-release: v0.92.3: a bet by hand frees the bet sheet once Novig has the 
  9040b4ca7 ckpt 3087: pre-ship: v0.92.3: a bet by hand frees the bet sheet once Novig has the ord
  4eb4e8141 ckpt 3086: Tj asked for a profit-proven bets/bids toggle (TPF1-5 in TASKS.md). Also: b
  69af95ebf ckpt 3085: pre-release: v0.92.2: timing of the whole bet placement in the event log (t
  273c23a5e ckpt 3084: pre-ship: v0.92.2: timing of the whole bet placement in the event log (the 
```
