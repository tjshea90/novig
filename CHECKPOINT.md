# CHECKPOINT 2972 — read me first, then TASKS.md

**Written:** 2026-10-11T16:36:19Z · **tests:** all 4 fast checks green
**Branch:** `claude/vigilant-scanner-enhancements-q97yhz` · **builds on:** `a7b59d791` (this checkpoint is the commit after it)

## Just done
TN code + tests green in data (real start sync, sharp-history close preferred, upgrade pass, independent CLV)

## Do this next
TL: remove OutsideSgo/OutsideOp resting + TennisOnly + exclusive otherBooks + pinnoddsActive coupling in VigilantApp; audit every call site vs toggles; tests; then app tests, bump 204, ship v0.89.19

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  bae53b9ee ckpt 2971: TM done in data: worst-case devig default + migration (schema 17) + CloseDe
  2fae65f9c ckpt 2970: wrote TM tasks (conservative devig); TL audit found: OutsideSgo/OutsideOp r
  03149e103 ckpt 2969: wrote TL tasks (all APIs on are used, off are never called)
  5b5f266b3 ckpt 2968: v0.89.18 shipped (share all bets, archive, stats filters, closing-odds fixe
  0778c1fda ckpt 2967: pre-release: v0.89.18: Share all bets with Claude (README + parts, every be
  8264b0eb4 ckpt 2966: pre-ship: v0.89.18: Share all bets with Claude (README + parts, every bet a
  085decdb5 ckpt 2965: Saved the closing-odds audit (research/closing_audit_2026-10-11.md, 25 unve
  52dd230bf ckpt 2964: TK tests (BetHistoryTest, BetHistoryUiTest, BetArchiveAppTest) pass; RESEAR
  654499774 ckpt 2963: TK: core built — BetRating.estimate/AtBet stars, BetDims+BetFilterSpec+sm
  a5b76868d ckpt 2962: wrote TK tasks (share all bets + stats filters + daily autosave)
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
