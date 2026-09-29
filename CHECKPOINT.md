# CHECKPOINT 2087 — read me first, then TASKS.md

**Written:** 2026-09-29T17:55:27Z · **tests:** all 2 fast checks green
**Branch:** `ccr-538c37db-finzj9` · **builds on:** `aef5b236` (this checkpoint is the commit after it)

## Just done
Y1/Y2: first real API orders refused for clientId format (vigilant- prefix); plain UUID for clientId and clientTransferId, placeOrder refuses non-UUIDs, mock Novig enforces it, lost-answer lookup covers PENDING/outcome/pages, OPEN with 0 remaining is finished; spec re-read; docs; version 0.21.2/48

## Do this next
Y3: full floor, ship.sh v0.21.2, wait CI, release.yml, record-release, tell Tj to try the bet again

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  5984e7b7 ckpt 2086: v0.21.1 (code 47) released and recorded; X1-X5 ticked
  55cdc46b ckpt 2085: pre-release: v0.21.1: Check odds now updates the EV of every open bet, Vigil
  7d4c504e ckpt 2084: X2-X4 ticked with their tests; RESEARCH.md §38.3 (built, live 10/10 priced 
  a080d3fb ckpt 2083: X2d/X3/X4 built and tested: bets-only pricing pass wired into Check odds now
  e3d39422 ckpt 2082: X2d/X3/X4 code: BetRecheck.Report/Plan count the Vigilant pricing pass, chec
  507fe0e7 ckpt 2081: X2a-c done: Scanner(betsOnly), BetsScope, BetPricingReasons, OpenBetPricer (
  c5a5cc70 ckpt 2080: X1 done (RESEARCH.md §38, TASKS X2-X4 broken into steps); X2a Scanner(betsO
  cc813115 ckpt 2079: Recorded Tj's tracker request (recheck every open bet incl. Vigilant scanner
  4101c936 ckpt 2078: v0.21.0 (code 46) released and recorded; W9 ticked
  57fa81d3 ckpt 2077: pre-release: v0.21.0: bet through Novig's API from a separate Vigilant walle
```

(18 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
