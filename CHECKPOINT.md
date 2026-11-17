# CHECKPOINT 4614 — read me first, then TASKS.md

**Written:** 2026-11-17T23:01:10Z · **tests:** all 10 fast checks green
**Branch:** `ccr-f4518318-aidjgt` · **builds on:** `cd4c4200f` (this checkpoint is the commit after it)

## Just done
PLS: full :cloud/:data suites green (3041 passed); Pinnacle cache age, PropLine upgrade watch, game_state on socket 2, docs + CHANGES + research

## Do this next
land on main, then engine_set --reset so the runner starts on the resubscribed key; read the engine log for PropLine holds/ready/game_state

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  94eb2c719 ckpt 4613: PLS1 plan watch + PLS3 PropLine game_state socket (steam/ev/resolution + li
  2600e036d ckpt 4612: PLS: logged Tj's request, saved PropLine docs 2026-11-17, diagnosed: ledger
  c401c0f14 ckpt 4611: BV3 logged: SharpAPI no-start-time drop is the concrete lever
  29dfa661e ckpt 4610: BV2: bid volume cause = feeds (ParlayAPI 0, PropLine spent, SGO silent), we
  1c9591424 ckpt 4609: BV1: not-bid reasons live; asked Tj which floor to relax
  fc2806403 ckpt 4608: engine bid status now lists top 3 not-bid reasons (CloudBidRunner); CloudBi
  1be3f8263 ckpt 4607: chat 20:2x: ttl 60 for bid volume, replied to Tj
  a0d22103d ckpt 4606: pre-release: HB1 hard block (NCAAB exempt), NCAAB scroll memory, bids deplo
  d35d16356 ckpt 4605: pre-ship: HB1 hard block (NCAAB exempt), NCAAB scroll memory, bids deploy
  621e790cd ckpt 4604: answered chat: wallet is $67, bid churn found
```

(8 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
