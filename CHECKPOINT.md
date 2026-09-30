# CHECKPOINT 2160 — read me first, then TASKS.md

**Written:** 2026-09-30T04:45:25Z · **tests:** all 3 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `b33a0d90` (this checkpoint is the commit after it)

## Just done
K1-K2 done: Check odds now tells 'CNO didn't answer' from 'page doesn't list your line' (the real cause of the early stop), keeps going, ParlayAPI backup (ParlayBooks) prices with CNO's own check; tests green

## Do this next
I5: docs pass (endpoints/params/costs vs docs), RESEARCH §45; then floor (bash tools/test.sh), sweep, ship v0.29.0 (code 57), release, answer

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  ece79c95 ckpt 2159: J1-J2 done: +EV ✕ with Undo and Put back (FeedRemoveTest 3 green)
  8d36a404 ckpt 2158: J2 coded: +EV cards get CNO's ✕ (hideOpportunity -> markHidden, same place
  90340774 ckpt 2157: ParlayAPI alternates bought only when no Pinnacle feed is on (marketsFor alt
  1808bb5e ckpt 2156: I4: ParlayAccount reads /v1/usage (credits month, reset Oct 1) with api-key-
  4f81f856 ckpt 2155: Real-key probe findings coded (ParlayMarkets normalizer, NHL props, flat clo
  36f64899 ckpt 2154: Recorded Tj's key-sharing request as I1-I3 (key kept in scratchpad only, mas
  3d70cba6 ckpt 2153: v0.28.0 (code 56) released and recorded; H1-H6 ticked
  c974b2a9 ckpt 2152: pre-release: v0.28.0: ParlayAPI fixes from Tj's diagnostics and its best pra
  2661b457 ckpt 2151: pre-ship: v0.28.0: ParlayAPI fixes from Tj's diagnostics and its best practi
  5cfff92b ckpt 2150: H1-D Runway counts pinnapi behind PinnWire (RunwayTest); H5 maxAgeSec=600 on
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
