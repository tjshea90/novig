# CHECKPOINT 2085 — read me first, then TASKS.md

**Written:** 2026-09-29T17:20:41Z · **tests:** all 2 fast checks green
**Branch:** `ccr-538c37db-finzj9` · **builds on:** `7d4c504e` (this checkpoint is the commit after it)

## Just done
pre-release: v0.21.1: Check odds now updates the EV of every open bet, Vigilant-scanner bets included (priced from Vigilant's own fair odds in a bets-only pass; bets CNO couldn't read too), each bet shows a current EV or why not, and 'now' only while the read is young; Tracker bets sort by date placed, current EV, amount, game start and filter by scanner; placed date on each card (versionCode 47, v0.21.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.21.1), then run: bash tools/record-release.sh v0.21.1 47 "v0.21.1: Check odds now updates the EV of every open bet, Vigilant-scanner bets included (priced from Vigilant's own fair odds in a bets-only pass; bets CNO couldn't read too), each bet shows a current EV or why not, and 'now' only while the read is young; Tracker bets sort by date placed, current EV, amount, game start and filter by scanner; placed date on each card"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  7d4c504e ckpt 2084: X2-X4 ticked with their tests; RESEARCH.md §38.3 (built, live 10/10 priced 
  a080d3fb ckpt 2083: X2d/X3/X4 built and tested: bets-only pricing pass wired into Check odds now
  e3d39422 ckpt 2082: X2d/X3/X4 code: BetRecheck.Report/Plan count the Vigilant pricing pass, chec
  507fe0e7 ckpt 2081: X2a-c done: Scanner(betsOnly), BetsScope, BetPricingReasons, OpenBetPricer (
  c5a5cc70 ckpt 2080: X1 done (RESEARCH.md §38, TASKS X2-X4 broken into steps); X2a Scanner(betsO
  cc813115 ckpt 2079: Recorded Tj's tracker request (recheck every open bet incl. Vigilant scanner
  4101c936 ckpt 2078: v0.21.0 (code 46) released and recorded; W9 ticked
  57fa81d3 ckpt 2077: pre-release: v0.21.0: bet through Novig's API from a separate Vigilant walle
  535e01eb ckpt 2076: W9: placement never cancelled by closing the sheet (+test that failed withou
  a9194471 ckpt 2075: ApiSettler: bets sharing a market are graded together (one market payout no 
```
