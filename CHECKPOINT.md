# CHECKPOINT 4383 — read me first, then TASKS.md

**Written:** 2026-11-05T06:09:21Z · **tests:** 1 RED: test_research_index.sh (8 green)
**Branch:** `ccr-c53b8db3-i98qqw` · **builds on:** `ba32c2c82` (this checkpoint is the commit after it)

## Just done
NC1: engine publishes ncaab.json.gz (stageNcaab), CloudLink/CloudClient fetch, NCAAB tab (schedule cards, leans, unlike-the-averages, game sheet with Books&EV and alternate dropdowns, Bet buttons via betWithFair), NcaabUiTest 8 green

## Do this next
NC1: parlay helper (keyless Novig quote + multi-leg deeplink) + UI, research docs + CLAUDE.md + NOVIG_API.md, tools/test_ncaab.sh fast check, INDEX/LEVERS, full test.sh, ship

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  876c1cb1b ckpt 4382: NC1: ncaab-log.yml workflow + 3-season seed (17,323 games) + backtest repor
  409991236 ckpt 4381: NC1: tools/ncaab_ats.py (ESPN log, ratings, backtest, signal tables), Kotli
  3f5551fad ckpt 4380: NC1: wrote the college-basketball ATS request into TASKS.md; ESPN core odds
  97dd98741 ckpt 4379: ANALYST 11-05: no new evidence, no change; STATE updated
  ff7b2e8e8 ckpt 4378: pre-release: A phone restart no longer turns off auto-bet, bids or auto-sca
  9a63bb669 ckpt 4377: pre-ship: A phone restart no longer turns off auto-bet, bids or auto-scan
  4e14dc81f ckpt 4376: pre-ship: A phone restart no longer turns off auto-bet, bids or auto-scan
  56a7b1fc7 ckpt 4375: LR1 restart no longer resets auto bids; engine off per Tj
  e78f2e2cd ckpt 4374: WATCH 11-04 slot1: engine healthy, nothing shipped
  ae085049f ckpt 4373: ENGINEER 11-04 slot0: engine healthy, nothing shipped
```

(17 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
