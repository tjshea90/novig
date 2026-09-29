# CHECKPOINT 2088 — read me first, then TASKS.md

**Written:** 2026-09-29T17:56:53Z · **tests:** all 2 fast checks green
**Branch:** `ccr-538c37db-finzj9` · **builds on:** `cbe4faa2` (this checkpoint is the commit after it)

## Just done
pre-release: v0.21.2: fix for the first real API bets: Novig refused both orders because the order's clientId must be a plain UUID (it had a vigilant- prefix); nothing was placed. The order and transfer ids are plain UUIDs and can't be anything else, the lost-answer lookup covers queued orders, and every other field of the placing path was re-checked against Novig's spec (versionCode 48, v0.21.2)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.21.2), then run: bash tools/record-release.sh v0.21.2 48 "v0.21.2: fix for the first real API bets: Novig refused both orders because the order's clientId must be a plain UUID (it had a vigilant- prefix); nothing was placed. The order and transfer ids are plain UUIDs and can't be anything else, the lost-answer lookup covers queued orders, and every other field of the placing path was re-checked against Novig's spec"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  cbe4faa2 ckpt 2087: Y1/Y2: first real API orders refused for clientId format (vigilant- prefix);
  5984e7b7 ckpt 2086: v0.21.1 (code 47) released and recorded; X1-X5 ticked
  55cdc46b ckpt 2085: pre-release: v0.21.1: Check odds now updates the EV of every open bet, Vigil
  7d4c504e ckpt 2084: X2-X4 ticked with their tests; RESEARCH.md §38.3 (built, live 10/10 priced 
  a080d3fb ckpt 2083: X2d/X3/X4 built and tested: bets-only pricing pass wired into Check odds now
  e3d39422 ckpt 2082: X2d/X3/X4 code: BetRecheck.Report/Plan count the Vigilant pricing pass, chec
  507fe0e7 ckpt 2081: X2a-c done: Scanner(betsOnly), BetsScope, BetPricingReasons, OpenBetPricer (
  c5a5cc70 ckpt 2080: X1 done (RESEARCH.md §38, TASKS X2-X4 broken into steps); X2a Scanner(betsO
  cc813115 ckpt 2079: Recorded Tj's tracker request (recheck every open bet incl. Vigilant scanner
  4101c936 ckpt 2078: v0.21.0 (code 46) released and recorded; W9 ticked
```
