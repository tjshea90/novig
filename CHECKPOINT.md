# CHECKPOINT 2973 — read me first, then TASKS.md

**Written:** 2026-10-11T16:43:42Z · **tests:** all 4 fast checks green
**Branch:** `claude/vigilant-scanner-enhancements-q97yhz` · **builds on:** `e0de31a1a` (this checkpoint is the commit after it)

## Just done
TL2: removed OutsideSgo/OutsideOp/TennisOnly, every API on is asked, off never; Pinnodds decoupled from SGO/OP; otherBooks asks all; lab asks SGO+OP; ApiTogglesTest

## Do this next
TL: finish call-site audit (parlayCloses.enabled paths, JS/Kt docs), update docs, run data+app tests, bump 204, ship v0.89.19

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  f2ecdb21d ckpt 2972: TN code + tests green in data (real start sync, sharp-history close preferr
  bae53b9ee ckpt 2971: TM done in data: worst-case devig default + migration (schema 17) + CloseDe
  2fae65f9c ckpt 2970: wrote TM tasks (conservative devig); TL audit found: OutsideSgo/OutsideOp r
  03149e103 ckpt 2969: wrote TL tasks (all APIs on are used, off are never called)
  5b5f266b3 ckpt 2968: v0.89.18 shipped (share all bets, archive, stats filters, closing-odds fixe
  0778c1fda ckpt 2967: pre-release: v0.89.18: Share all bets with Claude (README + parts, every be
  8264b0eb4 ckpt 2966: pre-ship: v0.89.18: Share all bets with Claude (README + parts, every bet a
  085decdb5 ckpt 2965: Saved the closing-odds audit (research/closing_audit_2026-10-11.md, 25 unve
  52dd230bf ckpt 2964: TK tests (BetHistoryTest, BetHistoryUiTest, BetArchiveAppTest) pass; RESEAR
  654499774 ckpt 2963: TK: core built — BetRating.estimate/AtBet stars, BetDims+BetFilterSpec+sm
```

(12 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
