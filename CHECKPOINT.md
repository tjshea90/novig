# CHECKPOINT 2577 — read me first, then TASKS.md

**Written:** 2026-10-05T20:20:41Z · **tests:** all 3 fast checks green
**Branch:** `ccr-cb9fb4b3-95tfr2` · **builds on:** `baa749eb` (this checkpoint is the commit after it)

## Just done
CP2: wash guard in MakerPlan (never two bids of ours on the two sides of a market adding to $1+; counts resting, cancelled-this-pass and coming-down bids), MakerTest +3, 5 mutants killed

## Do this next
Diagnostics: print the pass's waiting reasons; RESEARCH §91 (CP1 verdict: both sides fine, window pair EV-additive, variance lower); decide the optional strict switch; CP4 sweep + full floor + ship v0.67.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  fad2a893 ckpt 2576: CP3 built+tested: makerMaxOdds in MakerRules/outsideWindow (all bid paths), 
  d39aa899 ckpt 2575: CP3: longest-odds bid limit: rules + settings field + Bids settings chips/ty
  119cb5c1 ckpt 2574: TASKS.md: CP1-CP4 written from Tj's 19:53Z message (both sides of a prop gua
  d53f88d3 ckpt 2573: v0.66.0 released and recorded (CO1-CO7 done)
  dcaaa801 ckpt 2572: pre-release: v0.66.0: API audit against the providers' own docs (RESEARCH §
  e8e00ce0 ckpt 2571: pre-ship: v0.66.0: API audit against the providers' own docs (RESEARCH §90)
  93cffb3f ckpt 2570: docs: RESEARCH 90.8 built/left, NOVIG_API batch use, CO1-CO7 ticked
  02e9bbac ckpt 2569: Kalshi alternate-host fallback (404/410/unknown host -> external-api.kalshi.
  b9293edb ckpt 2568: Novig batch place/cancel in the bid desk (client flag, fallback to singles, 
  42f8cff7 ckpt 2567: RESEARCH §90 written: API audit (docs vs code, ranking, PropLine buy answer
```

(4 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
