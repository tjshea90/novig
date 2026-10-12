# CHECKPOINT 3088 — read me first, then TASKS.md

**Written:** 2026-10-12T15:05:32Z · **tests:** all 4 fast checks green
**Branch:** `ccr-eb2a51d8-1tw8ot` · **builds on:** `9040b4ca7` (this checkpoint is the commit after it)

## Just done
pre-release: v0.92.3: a bet by hand frees the bet sheet once Novig has the order (Novig takes ~5.4 s to end it): close the sheet or start the next bet; the order's dollars stay counted against the day and game limits and repeat guard until it is recorded; result arrives as a notification (versionCode 227, v0.92.3)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.92.3), then run: bash tools/record-release.sh v0.92.3 227 "v0.92.3: a bet by hand frees the bet sheet once Novig has the order (Novig takes ~5.4 s to end it): close the sheet or start the next bet; the order's dollars stay counted against the day and game limits and repeat guard until it is recorded; result arrives as a notification"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  9040b4ca7 ckpt 3087: pre-ship: v0.92.3: a bet by hand frees the bet sheet once Novig has the ord
  4eb4e8141 ckpt 3086: Tj asked for a profit-proven bets/bids toggle (TPF1-5 in TASKS.md). Also: b
  69af95ebf ckpt 3085: pre-release: v0.92.2: timing of the whole bet placement in the event log (t
  273c23a5e ckpt 3084: pre-ship: v0.92.2: timing of the whole bet placement in the event log (the 
  6112e744c ckpt 3083: Bet-sheet lag question (Tj): added timing line to every bet check (BET even
  064babb71 ckpt 3082: pre-release: v0.92.1: fixes slow scans after a reconnect (v0.92.0 carried t
  2c979489e ckpt 3081: pre-ship: v0.92.1: fixes slow scans after a reconnect (v0.92.0 carried the 
  0b6e32333 ckpt 3080: pre-release: v0.92.0: orders over Novig's websocket (trading-key connection
  26748a4b4 ckpt 3079: TNS5 stream history in event log; docs: NOVIG_API.md §27 (orders over the 
  18fee1961 ckpt 3078: TNS4: market status acted on: NovigBook.lifecycle from the stream; Pricing 
```
