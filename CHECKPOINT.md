# CHECKPOINT 2704 — read me first, then TASKS.md

**Written:** 2026-10-07T19:43:52Z · **tests:** all 4 fast checks green
**Branch:** `ccr-b67c8afa-h14i94` · **builds on:** `78a6c712` (this checkpoint is the commit after it)

## Just done
DJ (bets vs bids logging): TASKS.md DJ1-DJ6 written; scouting workflow wf_ecd174a8-7fd launched (data model, Tracker UI, Diagnostics, scan study + critic/design); facts so far: TrackedBet.maker + AtBet.HOW_BID exist, Tracker card says 'your bid, filled', StudyExport has bidSection, BidReport exists; no code changed yet

## Do this next
DJ1: read the scouting result (journal.jsonl of wf_ecd174a8-7fd, or re-scout), pick the single BET-vs-BID classifier (incl. the 11 untagged older fills), then build Tracker Bets/Bids/All chip + stats, Diagnostics split + bids section, study rows/splits + README; tests, floor, CI, ship, release

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M research/ACTIVE_LOG_2026-10-07.md

## Last ten checkpoints
```
  0e535d34 ckpt 2703: Tj's bets-vs-bids logging request written into TASKS.md (DJ1-DJ6)
  6195d011 ckpt 2702: v0.73.0 released and recorded; DI1-DI5 all done (v0.72.3, v0.72.4, v0.73.0)
  d800e268 ckpt 2701: pre-release: v0.73.0: the CrazyNinjaOdds list can be limited to leagues, kin
  0492de95 ckpt 2700: pre-release: v0.73.0: the CrazyNinjaOdds list can be limited to leagues, kin
  4f2b0a24 ckpt 2699: DI5: CnoScopeUiTest green (11): CNO tab league chips, 'reading with your new
  be3da5cd ckpt 2698: pre-release: v0.72.4: small-prop guard on the Auto-bet tab: no one kind of p
  ab16bcd3 ckpt 2697: pre-release: v0.72.4: small-prop guard (share cap with an even-split floor, 
  8efd407b ckpt 2696: DI3: small-prop guard core: PropGuard (share cap with even-split floor, per-
  f3b9fbb4 ckpt 2695: pre-release: v0.72.3: Low API usage no longer hard-sets the trap guard windo
  2d31323a ckpt 2694: pre-release: v0.72.3: Low API usage no longer hard-sets the trap guard windo
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
