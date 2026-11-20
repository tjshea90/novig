# CHECKPOINT 4793 — read me first, then TASKS.md

**Written:** 2026-11-20T18:58:44Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `099bb3160` (this checkpoint is the commit after it)

## Just done
PF1 DONE: parlay profit profile live on cloud-config (rfq-maker-engine.json, 81ac24d85), engine code + app 1.0.146 shipped (release.yml green), research/parlay_profit_profile_2026-11-20.md, SHAPES.txt in the daily study; engine is OFF (Tj's switch) so the first start after he turns it on runs the profile

## Do this next
when Tj turns the engine on: confirm status rfq.configLock is set and rfq.config shows maxLegs 2 / longshotsOnly false (engine-log 'RFQ maker config: LIVE'); after the daily study (10:37 UTC) read SHAPES.txt, judge on 20+ independent settled fills (clusters), tighten before widening; evening summary only if the engine runs

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  a047ad3ab ckpt 4792: pre-release: PF1: the parlay maker runs on Claude's profit profile; the Par
  69a013492 ckpt 4791: PF1: CLAUDE.md standing rule for the profile file; waiting for CI on d92a8f
  d92a8f21d ckpt 4790: PF1: profile written to cloud-config rfq-maker-engine.json (81ac24d85) and 
  cd41e97d7 ckpt 4789: PF1: engine profit profile file + precedence over the phone's rfq-maker.jso
  124c900f8 ckpt 4788: MK57: maxLegFair .8 -> .7 (Tj: sucker bets, not sharp heavy-favourite actio
  8ddbf3625 ckpt 4787: MK56: identical-parlay guard (maxSameParlay 1, maxParlaysPerOutcome 2) code
  bba77e4ab ckpt 4786: MK55: 16:20 check: job handover ok (44767621550), 8 fills today (15:37 FSU 
  1486dec0b ckpt 4785: MK54: walletLegShare 0.25 (same two legs filled twice in 7 minutes; per-leg
  e88e2d1a5 ckpt 4784: MK53: MK16 coverage follow-up answered from this session's work (no new bui
  48446df58 ckpt 4783: MK52: MK14 follow-up 2 report: market outcomes release stale (0 graded), ex
```
