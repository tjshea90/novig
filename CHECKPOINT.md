# CHECKPOINT 4387 — read me first, then TASKS.md

**Written:** 2026-11-05T07:29:28Z · **tests:** all 10 fast checks green
**Branch:** `ccr-0666b61b-yxwcj9` · **builds on:** `6ba1b8ed0` (this checkpoint is the commit after it)

## Just done
pre-release: NC1: NCAAB tab (college basketball ATS system with Novig lines, bets and a parlay builder) and its GitHub game log (versionCode 442, v1.0.102)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.102), then run: bash tools/record-release.sh v1.0.102 442 "NC1: NCAAB tab (college basketball ATS system with Novig lines, bets and a parlay builder) and its GitHub game log"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6ba1b8ed0 ckpt 4386: NC1 review fixes: ncaab-log.yml (data/ collision with the Gradle module + f
  8033d8408 ckpt 4385: RESUME as acct-27a37e (tj Gmail): merged acct-7542bf's unlanded NC1 branch 
  13c79d3a9 ckpt 4384: NC1: parlay builder (NovigParlay + ParlayBar), research note, NOVIG_API.md 
  fb9b99b9f ckpt 4383: NC1: engine publishes ncaab.json.gz (stageNcaab), CloudLink/CloudClient fet
  876c1cb1b ckpt 4382: NC1: ncaab-log.yml workflow + 3-season seed (17,323 games) + backtest repor
  409991236 ckpt 4381: NC1: tools/ncaab_ats.py (ESPN log, ratings, backtest, signal tables), Kotli
  3f5551fad ckpt 4380: NC1: wrote the college-basketball ATS request into TASKS.md; ESPN core odds
  97dd98741 ckpt 4379: ANALYST 11-05: no new evidence, no change; STATE updated
  ff7b2e8e8 ckpt 4378: pre-release: A phone restart no longer turns off auto-bet, bids or auto-sca
  9a63bb669 ckpt 4377: pre-ship: A phone restart no longer turns off auto-bet, bids or auto-scan
```
