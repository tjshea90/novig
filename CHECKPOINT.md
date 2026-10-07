# CHECKPOINT 2709 — read me first, then TASKS.md

**Written:** 2026-10-07T20:05:24Z · **tests:** all 4 fast checks green
**Branch:** `ccr-f242447d-q6sygj` · **builds on:** `35419387` (this checkpoint is the commit after it)

## Just done
DJ2 + DJ3 done in the tree: bid/bet rule + tagBids wiring, Tracker Bets/Bids chip (lists, stats, CLV card, profit line), BID tag, TrackerBidsUiTest 5 + StickyHeadersTest green

## Do this next
DJ4: Diagnostics - counts by kind in the Tracker section, 'Bets and bids apart' block via a shared function, MADE in the accuracy and as-placed splits, bid pipeline line (cancelled/expired/refused/voided + fill rate + ROI), then DJ5 study

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  7b4b02b7 ckpt 2708: DK1/DK2 done: researched CNO-only auto bids (RESEARCH §112; §111 stub for 
  29fe2dd7 ckpt 2707: DJ2 data layer written (TrackedBet.isBid, BetTracker.tagBids, BetOrBid, BetL
  544725eb ckpt 2706: DJ1 done: scouted the code (maker flag vs atBet.how, nothing reads both; Tra
  7a473229 ckpt 2705: Erased the off-topic fastboot/OTG question from INBOX.md (2 entries) and TAS
  ef2819e7 ckpt 2704: DJ (bets vs bids logging): TASKS.md DJ1-DJ6 written; scouting workflow wf_ec
  0e535d34 ckpt 2703: Tj's bets-vs-bids logging request written into TASKS.md (DJ1-DJ6)
  6195d011 ckpt 2702: v0.73.0 released and recorded; DI1-DI5 all done (v0.72.3, v0.72.4, v0.73.0)
  d800e268 ckpt 2701: pre-release: v0.73.0: the CrazyNinjaOdds list can be limited to leagues, kin
  0492de95 ckpt 2700: pre-release: v0.73.0: the CrazyNinjaOdds list can be limited to leagues, kin
  4f2b0a24 ckpt 2699: DI5: CnoScopeUiTest green (11): CNO tab league chips, 'reading with your new
```

(9 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
