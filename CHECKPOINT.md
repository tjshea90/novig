# CHECKPOINT 2083 — read me first, then TASKS.md

**Written:** 2026-09-29T17:12:35Z · **tests:** all 2 fast checks green
**Branch:** `ccr-538c37db-finzj9` · **builds on:** `e2efe373` (this checkpoint is the commit after it)

## Just done
X2d/X3/X4 built and tested: bets-only pricing pass wired into Check odds now (+ single-bet Price now), honest EV age on the card and sheet, Sort (Needs a look / Date placed / Current EV / Amount / Game start, tap again to flip) and Scanner filter rows, placed date on the card; TrackerUiTest +6, screenshots 4d/4g

## Do this next
Full floor (bash tools/test.sh), then an adversarial sweep of the diff (concurrency of the second Scanner, settings widening, metered-source use, observe vs applyPricing, CNO-only mode, MGM build unaffected), tick X2-X5, NOVIG_API/RESEARCH notes, ship v0.21.1

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  e3d39422 ckpt 2082: X2d/X3/X4 code: BetRecheck.Report/Plan count the Vigilant pricing pass, chec
  507fe0e7 ckpt 2081: X2a-c done: Scanner(betsOnly), BetsScope, BetPricingReasons, OpenBetPricer (
  c5a5cc70 ckpt 2080: X1 done (RESEARCH.md §38, TASKS X2-X4 broken into steps); X2a Scanner(betsO
  cc813115 ckpt 2079: Recorded Tj's tracker request (recheck every open bet incl. Vigilant scanner
  4101c936 ckpt 2078: v0.21.0 (code 46) released and recorded; W9 ticked
  57fa81d3 ckpt 2077: pre-release: v0.21.0: bet through Novig's API from a separate Vigilant walle
  535e01eb ckpt 2076: W9: placement never cancelled by closing the sheet (+test that failed withou
  a9194471 ckpt 2075: ApiSettler: bets sharing a market are graded together (one market payout no 
  32c7dac5 ckpt 2074: W1-W8 ticked; RESEARCH.md §36.6 (MoneyLine tested) and §37 (API betting de
  2741c198 ckpt 2073: W6-W8 app layer done: connection store + container wiring, ApiBettingControl
```

(20 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
