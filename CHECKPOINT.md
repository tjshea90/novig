# CHECKPOINT 4386 — read me first, then TASKS.md

**Written:** 2026-11-05T07:25:33Z · **tests:** all 10 fast checks green
**Branch:** `ccr-0666b61b-yxwcj9` · **builds on:** `83071831d` (this checkpoint is the commit after it)

## Just done
NC1 review fixes: ncaab-log.yml (data/ collision with the Gradle module + frozen weekly backtest, proven by new tools/workflow_dryrun.py: old FAIL, fixed PASS), ParlayBar late-price race (test red on old code), left-out label, Bet chip on backed card, NCAAB screenshot tests (looked at), version 1.0.102/442, CLAUDE.md+TASKS.md NC1h

## Do this next
run the FULL bash tools/test.sh on this exact content; if green run bash ship.sh 'NC1: college basketball ATS tab...'; then dispatch ncaab-log.yml on main and check its run; trigger release.yml; confirm; tools/record-release.sh v1.0.102 442; tick NC1f; tell Tj the NC1 verdict + Novig SYSTEM_LOCKED + what I fixed

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  8033d8408 ckpt 4385: RESUME as acct-27a37e (tj Gmail): merged acct-7542bf's unlanded NC1 branch 
  13c79d3a9 ckpt 4384: NC1: parlay builder (NovigParlay + ParlayBar), research note, NOVIG_API.md 
  fb9b99b9f ckpt 4383: NC1: engine publishes ncaab.json.gz (stageNcaab), CloudLink/CloudClient fet
  876c1cb1b ckpt 4382: NC1: ncaab-log.yml workflow + 3-season seed (17,323 games) + backtest repor
  409991236 ckpt 4381: NC1: tools/ncaab_ats.py (ESPN log, ratings, backtest, signal tables), Kotli
  3f5551fad ckpt 4380: NC1: wrote the college-basketball ATS request into TASKS.md; ESPN core odds
  97dd98741 ckpt 4379: ANALYST 11-05: no new evidence, no change; STATE updated
  ff7b2e8e8 ckpt 4378: pre-release: A phone restart no longer turns off auto-bet, bids or auto-sca
  9a63bb669 ckpt 4377: pre-ship: A phone restart no longer turns off auto-bet, bids or auto-scan
  4e14dc81f ckpt 4376: pre-ship: A phone restart no longer turns off auto-bet, bids or auto-scan
```

(32 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
