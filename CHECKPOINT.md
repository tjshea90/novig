# CHECKPOINT 4773 — read me first, then TASKS.md

**Written:** 2026-11-20T07:13:58Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `65b3c28c5` (this checkpoint is the commit after it)

## Just done
MK43: floors cut to ~fair (margin .001, fairErrorRel .001, minPoints .0005, minReturn .001), in-play floor livePremium .008 / .0015 per leg; evidence: 811 quotes, 9 on executed RFQs, our price +0.9 pt over theirs

## Do this next
watch won/filled at the 07:40 wake; no reset before 08:00; fix explain() label for alternate game lines at the next deploy

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  ea125d9d9 ckpt 4772: MK41 fix: Novig answered 400 INVALID_QUERY to the combined protected-type l
  cdd39e1bd ckpt 4771: MK42: floors halved (margin .003, fairErrorRel .003, minPoints .001, minRet
  be280dcae ckpt 4770: MK40/MK41: coverage fixes (protected prop targets, Yes-only anytime-TD list
  e93e1ba3a ckpt 4769: MK39: maxStake 12 (a code default, not Tj's) -> 100, walletShare .75, liveM
  3d1d963bc ckpt 4768: MK38: propPairsCashReserve (a prop-pair quote leaves $60 of free cash to th
  0ad71f9a4 ckpt 4767: MK37 evidence: where fills come from (morning vs now, executed-trade win sh
  3abb65590 ckpt 4766: MK37: in-play same-game pairs (moneyline/spread + total) priced through the
  1d27b8673 ckpt 4765: MK36: decline 3+ leg parlays whose legs are all favourites (study: -20.6% o
  ed905ede7 ckpt 4764: MK31e: live fair current-as-of-now for stream-held books, watch up to 600 m
  2b82c5518 ckpt 4763: MK35: pregame 3+ leg parlays need a longshot leg (manyLegsNeedLongshot); ba
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
