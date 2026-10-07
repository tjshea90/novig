# CHECKPOINT 2683 — read me first, then TASKS.md

**Written:** 2026-10-07T11:26:41Z · **tests:** all 4 fast checks green
**Branch:** `ccr-b67c8afa-h14i94` · **builds on:** `08a5f860` (this checkpoint is the commit after it)

## Just done
DH2 app side: live feed test (ScanSettings.feedRace OFF by default, VigilantApp runner + tick, view model line + share, Settings section, Diagnostics section, FeedRaceText); floor 2468 green; 10 mutants killed

## Do this next
analyze tape2 (odds) for RESEARCH 106; bump 0.72.1 code 127 + RESEARCH, wait for CI green on the exact commit, ship, release, record; then DH5 audit, DH4 full tests

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M research/ACTIVE_LOG_2026-10-07.md

## Last ten checkpoints
```
  686eb39e ckpt 2682: DH2: FeedRaceRunner (every feed read only while Novig has a live game it cov
  13f7cc75 ckpt 2681: DH2: FeedParsers (Sofascore, Polymarket score+odds, ESPN, NHL, MLB) and Feed
  04d0cb5b ckpt 2680: v0.72.0 released and recorded; DH2 first measured numbers (tennis tape, RESE
  4eee78e0 ckpt 2679: DH3: batch-place reply array shape fixed (4 occurrences in Tj's file), DoH c
  7ff1919e ckpt 2678: pre-release: v0.72.0: small-market bid fill behind the popular bids (Quick &
  92453213 ckpt 2677: v0.72.0 candidate complete: obscure fill + rec 11 + recs 2/4/5/9/10, RESEARC
  a16d36aa ckpt 2676: DG4 code done (Kalshi 3 req/s test, setting, Diagnostics line, tests); TASKS
  c381f06c ckpt 2675: DG5 done (unread Novig trades skip game-line auto-bet CNO+Pinnacle and game-
  83d81cee ckpt 2674: DG3 done: gate and Kelly size on the lower of CNO's edge and the books' own 
  1e148a6f ckpt 2673: DG2 done: favourites need 1 point more edge (setting, chips, typed box, judg
```

(8 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
