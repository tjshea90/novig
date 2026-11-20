# CHECKPOINT 4789 — read me first, then TASKS.md

**Written:** 2026-11-20T18:34:09Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `f252b6a24` (this checkpoint is the commit after it)

## Just done
PF1: engine profit profile file + precedence over the phone's rfq-maker.json (RfqConfigSource), configLock banner in the Parlays tab, over-limit parlays priced for the journal, rfq_shape_study + SHAPES.txt in the daily study; full suite green (4511 passed); research note, CHANGES, TASKS, LEVERS

## Do this next
land on main (push.sh), then write the profile with rfq_set.py (mode LIVE maxLegs 2 maxLegFair .8 maxFair .65 longshotsOnly off onlyToday off maxDaysAhead 2 dayLossStopPct .3) and mirror it into rfq-maker.json; verify via a dry read; tell Tj once; ship the app release (banner)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  124c900f8 ckpt 4788: MK57: maxLegFair .8 -> .7 (Tj: sucker bets, not sharp heavy-favourite actio
  8ddbf3625 ckpt 4787: MK56: identical-parlay guard (maxSameParlay 1, maxParlaysPerOutcome 2) code
  bba77e4ab ckpt 4786: MK55: 16:20 check: job handover ok (44767621550), 8 fills today (15:37 FSU 
  1486dec0b ckpt 4785: MK54: walletLegShare 0.25 (same two legs filled twice in 7 minutes; per-leg
  e88e2d1a5 ckpt 4784: MK53: MK16 coverage follow-up answered from this session's work (no new bui
  48446df58 ckpt 4783: MK52: MK14 follow-up 2 report: market outcomes release stale (0 graded), ex
  d8818a462 ckpt 4782: MK51: 14:00 check: 6 fills today (13:56 .503/.501 $9.52), 1,278 quotes, cas
  465ad0a3a ckpt 4781: MK50: 11:25 check: 5 fills today (latest 10:49), 4 of 8 auctions entered wo
  85bd5b7de ckpt 4780: MK49: first fills since the morning (3 in job 44706378771 + 1 at 07:36): 2-
  1720bc703 ckpt 4779: MK48: 3% error premium per extrapolated game-line leg (alt-line check: medi
```

(19 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
