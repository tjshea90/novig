# CHECKPOINT 4382 — read me first, then TASKS.md

**Written:** 2026-11-05T05:55:53Z · **tests:** 1 RED: test_research_index.sh (8 green)
**Branch:** `ccr-c53b8db3-i98qqw` · **builds on:** `292427f41` (this checkpoint is the commit after it)

## Just done
NC1: ncaab-log.yml workflow + 3-season seed (17,323 games) + backtest report; evidence-based 'backed' rule (95% bound vs Novig price)

## Do this next
NC1: engine publishes ncaab.json.gz (CloudMain Publisher + CloudStatus ncaabAtMs), then CloudClient/CloudLink fetch, NCAAB tab UI, bet buttons, parlay helper, docs

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  409991236 ckpt 4381: NC1: tools/ncaab_ats.py (ESPN log, ratings, backtest, signal tables), Kotli
  3f5551fad ckpt 4380: NC1: wrote the college-basketball ATS request into TASKS.md; ESPN core odds
  97dd98741 ckpt 4379: ANALYST 11-05: no new evidence, no change; STATE updated
  ff7b2e8e8 ckpt 4378: pre-release: A phone restart no longer turns off auto-bet, bids or auto-sca
  9a63bb669 ckpt 4377: pre-ship: A phone restart no longer turns off auto-bet, bids or auto-scan
  4e14dc81f ckpt 4376: pre-ship: A phone restart no longer turns off auto-bet, bids or auto-scan
  56a7b1fc7 ckpt 4375: LR1 restart no longer resets auto bids; engine off per Tj
  e78f2e2cd ckpt 4374: WATCH 11-04 slot1: engine healthy, nothing shipped
  ae085049f ckpt 4373: ENGINEER 11-04 slot0: engine healthy, nothing shipped
  cf16cbb86 ckpt 4372: pre-release: Odds API keys used in order (a throttle no longer moves the en
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
