# CHECKPOINT 4775 — read me first, then TASKS.md

**Written:** 2026-11-20T07:32:28Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `42e9d2a75` (this checkpoint is the commit after it)

## Just done
MK45: propCashReserve ($90 of free cash kept from any prop-involving quote: props are 53% of RFQs and trade 100x less per collateral-minute than in-play game lines) + reserveAfterCloseMs 36 s; census label for alternate lines; full suite 4496 green

## Do this next
08:00+: ONE graceful reset to deploy MK45 + the census label (board note first), then rfq_set propCashReserve is already the default 90; watch quotes/min and cash-short share; no reset before 08:00

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  2a06e1f66 ckpt 4774: MK44 standing job recorded; census label fix for alternate game lines (clou
  b331e5550 ckpt 4773: MK43: floors cut to ~fair (margin .001, fairErrorRel .001, minPoints .0005,
  ea125d9d9 ckpt 4772: MK41 fix: Novig answered 400 INVALID_QUERY to the combined protected-type l
  cdd39e1bd ckpt 4771: MK42: floors halved (margin .003, fairErrorRel .003, minPoints .001, minRet
  be280dcae ckpt 4770: MK40/MK41: coverage fixes (protected prop targets, Yes-only anytime-TD list
  e93e1ba3a ckpt 4769: MK39: maxStake 12 (a code default, not Tj's) -> 100, walletShare .75, liveM
  3d1d963bc ckpt 4768: MK38: propPairsCashReserve (a prop-pair quote leaves $60 of free cash to th
  0ad71f9a4 ckpt 4767: MK37 evidence: where fills come from (morning vs now, executed-trade win sh
  3abb65590 ckpt 4766: MK37: in-play same-game pairs (moneyline/spread + total) priced through the
  1d27b8673 ckpt 4765: MK36: decline 3+ leg parlays whose legs are all favourites (study: -20.6% o
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
