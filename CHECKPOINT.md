# CHECKPOINT 2707 — read me first, then TASKS.md

**Written:** 2026-10-07T19:57:29Z · **tests:** all 4 fast checks green
**Branch:** `ccr-f242447d-q6sygj` · **builds on:** `d84d7740` (this checkpoint is the commit after it)

## Just done
DJ2 data layer written (TrackedBet.isBid, BetTracker.tagBids, BetOrBid, BetLedger made/Split.MADE, TrackerBreakdown By.MADE/describe, PropGuard reads isBid, BetOrBidTest); Tj asked mid-turn whether CNO-only auto bids are plausible: written into TASKS.md as DK1-DK3

## Do this next
re-run BetOrBidTest/BetLedgerTest/TrackerBreakdownTest/PropGuardTest; research DK1 (MakerLines/MakerQuote/MakerRunner vs CNO rows) and answer Tj; then DJ3 Tracker chip

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M data/src/test/kotlin/com/tjshea/vigilant/data/tracker/BetOrBidTest.kt

## Last ten checkpoints
```
  544725eb ckpt 2706: DJ1 done: scouted the code (maker flag vs atBet.how, nothing reads both; Tra
  7a473229 ckpt 2705: Erased the off-topic fastboot/OTG question from INBOX.md (2 entries) and TAS
  ef2819e7 ckpt 2704: DJ (bets vs bids logging): TASKS.md DJ1-DJ6 written; scouting workflow wf_ec
  0e535d34 ckpt 2703: Tj's bets-vs-bids logging request written into TASKS.md (DJ1-DJ6)
  6195d011 ckpt 2702: v0.73.0 released and recorded; DI1-DI5 all done (v0.72.3, v0.72.4, v0.73.0)
  d800e268 ckpt 2701: pre-release: v0.73.0: the CrazyNinjaOdds list can be limited to leagues, kin
  0492de95 ckpt 2700: pre-release: v0.73.0: the CrazyNinjaOdds list can be limited to leagues, kin
  4f2b0a24 ckpt 2699: DI5: CnoScopeUiTest green (11): CNO tab league chips, 'reading with your new
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
