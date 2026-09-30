# CHECKPOINT 2161 — read me first, then TASKS.md

**Written:** 2026-09-30T04:49:13Z · **tests:** all 3 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `f317c801` (this checkpoint is the commit after it)

## Just done
I5 docs pass fixes: include_live on ParlayAPI /odds when live is on, closing-lines bounded by the plan's history (Starter 7d from /v1/meta/limits, ParlayAccount.historyDays), X-Rate-Limit-* spelling read; tests green

## Do this next
RESEARCH §45 (real-key findings + docs pass + features to offer Tj), then floor, sweep, ship v0.29.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  400540ff ckpt 2160: K1-K2 done: Check odds now tells 'CNO didn't answer' from 'page doesn't list
  ece79c95 ckpt 2159: J1-J2 done: +EV ✕ with Undo and Put back (FeedRemoveTest 3 green)
  8d36a404 ckpt 2158: J2 coded: +EV cards get CNO's ✕ (hideOpportunity -> markHidden, same place
  90340774 ckpt 2157: ParlayAPI alternates bought only when no Pinnacle feed is on (marketsFor alt
  1808bb5e ckpt 2156: I4: ParlayAccount reads /v1/usage (credits month, reset Oct 1) with api-key-
  4f81f856 ckpt 2155: Real-key probe findings coded (ParlayMarkets normalizer, NHL props, flat clo
  36f64899 ckpt 2154: Recorded Tj's key-sharing request as I1-I3 (key kept in scratchpad only, mas
  3d70cba6 ckpt 2153: v0.28.0 (code 56) released and recorded; H1-H6 ticked
  c974b2a9 ckpt 2152: pre-release: v0.28.0: ParlayAPI fixes from Tj's diagnostics and its best pra
  2661b457 ckpt 2151: pre-ship: v0.28.0: ParlayAPI fixes from Tj's diagnostics and its best practi
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
