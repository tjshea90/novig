# CHECKPOINT 2090 — read me first, then TASKS.md

**Written:** 2026-09-29T18:21:03Z · **tests:** all 2 fast checks green
**Branch:** `ccr-538c37db-finzj9` · **builds on:** `b964d633` (this checkpoint is the commit after it)

## Just done
Recorded Tj's request (grading check for API bets, what to show me to optimize/verify, CNO only must not scan Vigilant in the background) as Z1-Z4

## Do this next
Z1: audit every background/Vigilant path under scanner=CNO only (AutoScan*, widget rescan, captureClosing, checkOdds/pricer, SettleWorker, alerts, NovigLive, ApiBetSync/Settler)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  9f71aed4 ckpt 2089: v0.21.2 (code 48) released and recorded; Y1-Y3 ticked
  ec0c2cc9 ckpt 2088: pre-release: v0.21.2: fix for the first real API bets: Novig refused both or
  cbe4faa2 ckpt 2087: Y1/Y2: first real API orders refused for clientId format (vigilant- prefix);
  5984e7b7 ckpt 2086: v0.21.1 (code 47) released and recorded; X1-X5 ticked
  55cdc46b ckpt 2085: pre-release: v0.21.1: Check odds now updates the EV of every open bet, Vigil
  7d4c504e ckpt 2084: X2-X4 ticked with their tests; RESEARCH.md §38.3 (built, live 10/10 priced 
  a080d3fb ckpt 2083: X2d/X3/X4 built and tested: bets-only pricing pass wired into Check odds now
  e3d39422 ckpt 2082: X2d/X3/X4 code: BetRecheck.Report/Plan count the Vigilant pricing pass, chec
  507fe0e7 ckpt 2081: X2a-c done: Scanner(betsOnly), BetsScope, BetPricingReasons, OpenBetPricer (
  c5a5cc70 ckpt 2080: X1 done (RESEARCH.md §38, TASKS X2-X4 broken into steps); X2a Scanner(betsO
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
