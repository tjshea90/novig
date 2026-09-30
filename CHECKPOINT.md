# CHECKPOINT 2147 — read me first, then TASKS.md

**Written:** 2026-09-30T03:22:11Z · **tests:** all 3 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `137b077b` (this checkpoint is the commit after it)

## Just done
H1-B fixed: CNO pause reason kept (CnoState.lastPause/At, pauseFor), Check odds note keeps CNO's own counts (Report.cnoTried/Failed/Skipped, roundNote) - BetRecheckTest + CnoAgreementTest new

## Do this next
H1-C reopen closeFinal-without-close bets when a new close source is active (CloseSource id, TrackedBet.closeAskedOf); H1-D Runway PinnWire+pinnapi; H5 maxAgeSec on /props + commenceTimeTo on ParlayAPI /odds

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  97657671 ckpt 2146: Pacing bugs fixed (stale out-of-order answers ignored, STALE_WINDOW/SLACK; f
  4901bda3 ckpt 2145: Recorded Tj's diagnostics-review request as H1-H3
  18e92257 ckpt 2144: v0.27.0 (code 55) released and recorded (CI 36659840921 + release.yml 366601
  07ce7847 ckpt 2143: pre-release: v0.27.0: ParlayAPI fully used on its $5 Starter plan: Pinnacle'
  c5b40f47 ckpt 2142: pre-ship: v0.27.0: ParlayAPI fully used on its $5 Starter plan: Pinnacle's c
  2e0a6863 ckpt 2141: Full test findings #2-#3 fixed: CLV card copy now names Pinnacle's ParlayAPI
  ebc4a263 ckpt 2140: Full test finding #1 fixed: background auto-scans could spend ParlayAPI's wh
  fda54162 ckpt 2139: RESEARCH.md §43 written (5 sources, ParlayAPI tested endpoints/costs, what 
  bac95a85 ckpt 2138: ParlayAPI UI + switch: meter line (today's scan share / free = closes only),
  9178cca7 ckpt 2137: G1/G2 tests green (67): CreditPaceTest 5, ParlayPropsTest 7 (bulk props pars
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
