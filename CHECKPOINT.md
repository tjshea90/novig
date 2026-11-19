# CHECKPOINT 4704 — read me first, then TASKS.md

**Written:** 2026-11-19T18:58:42Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `c800e4075` (this checkpoint is the commit after it)

## Just done
MK14: LIVE capital hurdle liveMinReturn .03 (+ paper journaling), leg + EXEC_RESULT journaling, tools/rfq_outcomes.py graded from ESPN (rfq-market.yml), expected-profit study; FULL suite green (4449)

## Do this next
rides the next engine handover (no reset); after tonight's games read OUTCOMES.md (would-win adverse selection); 24 h probes -> marketPricing/propPairsFromMarket; coverage: prop legs (MK11 ladder) and prop+prop same-game pairs

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M research/INDEX.md

## Last ten checkpoints
```
  b6e0f5569 ckpt 4703: MK13 verified live: standing read 6/6 confirmed, 1 socket no reconnects, ex
  a78018e3f ckpt 4702: MK13c: per-outcome collateral cap (maxLegCollateral $30, LIVE), executions 
  6b65df2ba ckpt 4701: WATCH 2026-11-19: engine healthy, no action
  66b41859f ckpt 4700: MK13b: docs audit written (research/novig_rfq_docs_audit_2026-11-19.md), NO
  422e9a32a ckpt 4699: MK13a: Novig docs audit fixes in the RFQ maker: reconnect rules (RfqReconne
  5ca44ed7e ckpt 4698: MK11 17:38: ladder lifts priced trades 0.5% -> 19% on paper
  5517a1085 ckpt 4697: MK12 round 2 complete and deployed in SHADOW: last-look fix, SameGame copul
  36e09e625 ckpt 4696: MK11: ladder live in shadow; engine outage 16:59-17:10 repaired (dispatch),
  076fb99a1 ckpt 4695: MK12h: rfq-market.yml retries refused calls (the workflow token's 1,000 an 
  44115cb5c ckpt 4694: MK12g: RfqMarketModel.MIN_SUPPORT (5 answers above a candidate margin), wal
```

(23 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
