# CHECKPOINT 3089 — read me first, then TASKS.md

**Written:** 2026-10-12T15:13:23Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `123a74397` (this checkpoint is the commit after it)

## Just done
TPF1-4: profit-by-type analysis (research/profit_types_2026-10-12.md), ProfitProfile + tools/profit-profile.py + shipped profile json, ApiGates.profitProven Settings switch, planner/bid-desk/auto-bet ordering wiring, Diagnostics line, 10 tests; CLAUDE.md regeneration rule

## Do this next
run full floor (bash tools/test.sh), bump 0.93.0 code 228, ship.sh, CI via send_later, release.yml, record-release; v0.92.3 release pending (trigger trig_01SfUvQYEJGacJv2raHCNrP1 at 15:15Z)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M CLAUDE.md
     M TASKS.md
     M research/lab/NOTES.md
    ?? research/profit_types_2026-10-12.md
    ?? research/profit_types_raw_2026-10-12.txt

## Last ten checkpoints
```
  68c3a2724 ckpt 3088: pre-release: v0.92.3: a bet by hand frees the bet sheet once Novig has the 
  9040b4ca7 ckpt 3087: pre-ship: v0.92.3: a bet by hand frees the bet sheet once Novig has the ord
  4eb4e8141 ckpt 3086: Tj asked for a profit-proven bets/bids toggle (TPF1-5 in TASKS.md). Also: b
  69af95ebf ckpt 3085: pre-release: v0.92.2: timing of the whole bet placement in the event log (t
  273c23a5e ckpt 3084: pre-ship: v0.92.2: timing of the whole bet placement in the event log (the 
  6112e744c ckpt 3083: Bet-sheet lag question (Tj): added timing line to every bet check (BET even
  064babb71 ckpt 3082: pre-release: v0.92.1: fixes slow scans after a reconnect (v0.92.0 carried t
  2c979489e ckpt 3081: pre-ship: v0.92.1: fixes slow scans after a reconnect (v0.92.0 carried the 
  0b6e32333 ckpt 3080: pre-release: v0.92.0: orders over Novig's websocket (trading-key connection
  26748a4b4 ckpt 3079: TNS5 stream history in event log; docs: NOVIG_API.md §27 (orders over the 
```

(11 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
