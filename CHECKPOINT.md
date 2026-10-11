# CHECKPOINT 2967 — read me first, then TASKS.md

**Written:** 2026-10-11T16:04:55Z · **tests:** all 4 fast checks green
**Branch:** `claude/vigilant-scanner-enhancements-q97yhz` · **builds on:** `8264b0eb4` (this checkpoint is the commit after it)

## Just done
pre-release: v0.89.18: Share all bets with Claude (README + parts, every bet and bid), day-by-day archive in Downloads/Vigilant/bets, Stats smart filter and split by type/sport/stars/sharp/books/liquidity, bet record on each bet, closing-odds accuracy fixes (versionCode 203, v0.89.18)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.89.18), then run: bash tools/record-release.sh v0.89.18 203 "v0.89.18: Share all bets with Claude (README + parts, every bet and bid), day-by-day archive in Downloads/Vigilant/bets, Stats smart filter and split by type/sport/stars/sharp/books/liquidity, bet record on each bet, closing-odds accuracy fixes"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  8264b0eb4 ckpt 2966: pre-ship: v0.89.18: Share all bets with Claude (README + parts, every bet a
  085decdb5 ckpt 2965: Saved the closing-odds audit (research/closing_audit_2026-10-11.md, 25 unve
  52dd230bf ckpt 2964: TK tests (BetHistoryTest, BetHistoryUiTest, BetArchiveAppTest) pass; RESEAR
  654499774 ckpt 2963: TK: core built — BetRating.estimate/AtBet stars, BetDims+BetFilterSpec+sm
  a5b76868d ckpt 2962: wrote TK tasks (share all bets + stats filters + daily autosave)
  55fc3a342 ckpt 2961: v0.89.17 shipped (stars + wallet sleep); release.yml triggered
  6e39de29d ckpt 2960: pre-release: v0.89.17: 1-5 star bet ratings on both scanners with an auto-b
  3bdaccaab ckpt 2959: pre-ship: v0.89.17: 1-5 star bet ratings on both scanners with an auto-bet 
  3064b5783 ckpt 2958: wrote TI tasks (star ratings)
  dedfb335f ckpt 2957: v0.89.16 shipped (BookCompany researched, RESEARCH §130); release.yml trig
```
