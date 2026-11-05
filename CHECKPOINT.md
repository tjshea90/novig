# CHECKPOINT 4385 — read me first, then TASKS.md

**Written:** 2026-11-05T07:00:26Z · **tests:** all 10 fast checks green
**Branch:** `ccr-0666b61b-yxwcj9` · **builds on:** `487fe0205` (this checkpoint is the commit after it)

## Just done
RESUME as acct-27a37e (tj Gmail): merged acct-7542bf's unlanded NC1 branch (55 commits, clean merge 7b41cfe3a) into ccr-0666b61b-yxwcj9, took over the NC1 claim; reviewed engine loop, CloudLink, workflow, signals: no defects; ER2 verified done (engine LIVE); full tools/test.sh running locally and CI run 41983801150 on 7b41cfe3a

## Do this next
NC1: read the local test result + CI run 41983801150; fix anything red; add an NCAAB screenshot test (-Pscreenshots) and look at it; bump app/build.gradle.kts to 1.0.102 / code 442; bash ship.sh; trigger release.yml on main; confirm; tools/record-release.sh v1.0.102 442; tell Tj the NC1 verdict (the method does not beat closing lines) and that Novig is SYSTEM_LOCKED

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  13c79d3a9 ckpt 4384: NC1: parlay builder (NovigParlay + ParlayBar), research note, NOVIG_API.md 
  fb9b99b9f ckpt 4383: NC1: engine publishes ncaab.json.gz (stageNcaab), CloudLink/CloudClient fet
  876c1cb1b ckpt 4382: NC1: ncaab-log.yml workflow + 3-season seed (17,323 games) + backtest repor
  409991236 ckpt 4381: NC1: tools/ncaab_ats.py (ESPN log, ratings, backtest, signal tables), Kotli
  3f5551fad ckpt 4380: NC1: wrote the college-basketball ATS request into TASKS.md; ESPN core odds
  97dd98741 ckpt 4379: ANALYST 11-05: no new evidence, no change; STATE updated
  ff7b2e8e8 ckpt 4378: pre-release: A phone restart no longer turns off auto-bet, bids or auto-sca
  9a63bb669 ckpt 4377: pre-ship: A phone restart no longer turns off auto-bet, bids or auto-scan
  4e14dc81f ckpt 4376: pre-ship: A phone restart no longer turns off auto-bet, bids or auto-scan
  56a7b1fc7 ckpt 4375: LR1 restart no longer resets auto bids; engine off per Tj
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
