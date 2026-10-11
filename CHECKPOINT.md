# CHECKPOINT 2964 — read me first, then TASKS.md

**Written:** 2026-10-11T15:44:15Z · **tests:** all 4 fast checks green
**Branch:** `claude/vigilant-scanner-enhancements-q97yhz` · **builds on:** `a4f3920d6` (this checkpoint is the commit after it)

## Just done
TK tests (BetHistoryTest, BetHistoryUiTest, BetArchiveAppTest) pass; RESEARCH §132; review workflow wf_268ebe7b-004 + closing audit wf_cd962ddf-3e2 running

## Do this next
read both workflow results (journal.jsonl in subagents/workflows/), fix confirmed findings, add closeBasis field, run full suite, ship (versionCode 203/0.89.18), release

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  654499774 ckpt 2963: TK: core built — BetRating.estimate/AtBet stars, BetDims+BetFilterSpec+sm
  a5b76868d ckpt 2962: wrote TK tasks (share all bets + stats filters + daily autosave)
  55fc3a342 ckpt 2961: v0.89.17 shipped (stars + wallet sleep); release.yml triggered
  6e39de29d ckpt 2960: pre-release: v0.89.17: 1-5 star bet ratings on both scanners with an auto-b
  3bdaccaab ckpt 2959: pre-ship: v0.89.17: 1-5 star bet ratings on both scanners with an auto-bet 
  3064b5783 ckpt 2958: wrote TI tasks (star ratings)
  dedfb335f ckpt 2957: v0.89.16 shipped (BookCompany researched, RESEARCH §130); release.yml trig
  00be0f13a ckpt 2956: pre-release: v0.89.16: parent-company table researched from SGO and ParlayA
  3d5152d4e ckpt 2955: pre-ship: v0.89.16: parent-company table researched from SGO and ParlayAPI 
  bf0a24e57 ckpt 2954: v0.89.15 released and recorded; TH1-TH8 done
```

(8 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
