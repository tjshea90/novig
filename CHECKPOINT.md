# CHECKPOINT 4777 — read me first, then TASKS.md

**Written:** 2026-11-20T08:10:41Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `cefdacac1` (this checkpoint is the commit after it)

## Just done
MK37-MK46 shipped (live same-game pairs, prop-pair lane + cash reserve, stake cap lifted, coverage fixes, floors ~fair, alt-line extrapolation); engine job 44697283631 runs through MK45; MK46 on main not deployed; 0 wins so far at 8 quotes/min (cash-time bound)

## Do this next
PARLAY MANDATE LOOP trigger_017JPpLnJ23Y8NAFcMRFnmph fires 08:52 UTC: board, status, ONE reset >= 08:50 to deploy MK46, read 'RFQ alt-line check' + 'RFQ prop coverage', log any fill; US evening in-play is the real test; no 2 resets within an hour; revert lines for every change are in research/review/CHANGES.md (MK37-MK46)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  cefdacac1 ckpt 4776: MK46: bounded extrapolation of alternate game-line numbers outside the post
  c194ad0f2 ckpt 4775: MK45: propCashReserve ($90 of free cash kept from any prop-involving quote:
  2a06e1f66 ckpt 4774: MK44 standing job recorded; census label fix for alternate game lines (clou
  b331e5550 ckpt 4773: MK43: floors cut to ~fair (margin .001, fairErrorRel .001, minPoints .0005,
  ea125d9d9 ckpt 4772: MK41 fix: Novig answered 400 INVALID_QUERY to the combined protected-type l
  cdd39e1bd ckpt 4771: MK42: floors halved (margin .003, fairErrorRel .003, minPoints .001, minRet
  be280dcae ckpt 4770: MK40/MK41: coverage fixes (protected prop targets, Yes-only anytime-TD list
  e93e1ba3a ckpt 4769: MK39: maxStake 12 (a code default, not Tj's) -> 100, walletShare .75, liveM
  3d1d963bc ckpt 4768: MK38: propPairsCashReserve (a prop-pair quote leaves $60 of free cash to th
  0ad71f9a4 ckpt 4767: MK37 evidence: where fills come from (morning vs now, executed-trade win sh
```
