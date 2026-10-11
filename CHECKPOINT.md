# CHECKPOINT 2965 — read me first, then TASKS.md

**Written:** 2026-10-11T15:45:13Z · **tests:** all 4 fast checks green
**Branch:** `claude/vigilant-scanner-enhancements-q97yhz` · **builds on:** `5aabf8730` (this checkpoint is the commit after it)

## Just done
Saved the closing-odds audit (research/closing_audit_2026-10-11.md, 25 unverified findings) and stopped both workflows (Tj: workflows burn usage and fail on interruption)

## Do this next
TK5: triage the audit in the main process, ordered by severity wrong-number first (BetHistory closeLeadSec, history-close devig method, novig VWAP close, stale CNO read dating); then add closeBasis/independence to BetHistory.Row; full suite; ship 0.89.18

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  52dd230bf ckpt 2964: TK tests (BetHistoryTest, BetHistoryUiTest, BetArchiveAppTest) pass; RESEAR
  654499774 ckpt 2963: TK: core built — BetRating.estimate/AtBet stars, BetDims+BetFilterSpec+sm
  a5b76868d ckpt 2962: wrote TK tasks (share all bets + stats filters + daily autosave)
  55fc3a342 ckpt 2961: v0.89.17 shipped (stars + wallet sleep); release.yml triggered
  6e39de29d ckpt 2960: pre-release: v0.89.17: 1-5 star bet ratings on both scanners with an auto-b
  3bdaccaab ckpt 2959: pre-ship: v0.89.17: 1-5 star bet ratings on both scanners with an auto-bet 
  3064b5783 ckpt 2958: wrote TI tasks (star ratings)
  dedfb335f ckpt 2957: v0.89.16 shipped (BookCompany researched, RESEARCH §130); release.yml trig
  00be0f13a ckpt 2956: pre-release: v0.89.16: parent-company table researched from SGO and ParlayA
  3d5152d4e ckpt 2955: pre-ship: v0.89.16: parent-company table researched from SGO and ParlayAPI 
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
