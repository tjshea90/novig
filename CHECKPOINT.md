# CHECKPOINT 2970 — read me first, then TASKS.md

**Written:** 2026-10-11T16:17:40Z · **tests:** all 4 fast checks green
**Branch:** `claude/vigilant-scanner-enhancements-q97yhz` · **builds on:** `f0d06df2b` (this checkpoint is the commit after it)

## Just done
wrote TM tasks (conservative devig); TL audit found: OutsideSgo/OutsideOp rest paid feeds, TennisOnly override ignores off-switches, OtherBooks exclusive when SGO/OP on, pinnoddsActive needs SGO/OP, parlayInjuries off when SGO on

## Do this next
do TM (default WORST_CASE + migrate + CloseDevig worst-case), then TL fixes in VigilantApp.allReferenceSources/otherBooks/parlayInjuries/pinnoddsActive/tennis block; tests; ship

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  03149e103 ckpt 2969: wrote TL tasks (all APIs on are used, off are never called)
  5b5f266b3 ckpt 2968: v0.89.18 shipped (share all bets, archive, stats filters, closing-odds fixe
  0778c1fda ckpt 2967: pre-release: v0.89.18: Share all bets with Claude (README + parts, every be
  8264b0eb4 ckpt 2966: pre-ship: v0.89.18: Share all bets with Claude (README + parts, every bet a
  085decdb5 ckpt 2965: Saved the closing-odds audit (research/closing_audit_2026-10-11.md, 25 unve
  52dd230bf ckpt 2964: TK tests (BetHistoryTest, BetHistoryUiTest, BetArchiveAppTest) pass; RESEAR
  654499774 ckpt 2963: TK: core built — BetRating.estimate/AtBet stars, BetDims+BetFilterSpec+sm
  a5b76868d ckpt 2962: wrote TK tasks (share all bets + stats filters + daily autosave)
  55fc3a342 ckpt 2961: v0.89.17 shipped (stars + wallet sleep); release.yml triggered
  6e39de29d ckpt 2960: pre-release: v0.89.17: 1-5 star bet ratings on both scanners with an auto-b
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
