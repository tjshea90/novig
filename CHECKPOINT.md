# CHECKPOINT 2163 — read me first, then TASKS.md

**Written:** 2026-09-30T04:53:30Z · **tests:** all 3 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `df62a421` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.29.0: ParlayAPI matched to its docs and Tj's real key: the meter reads /v1/usage (credits month, exact reset), a new plan paced over the days left in its month, alternates only when no Pinnacle feed, live games asked for, closing lines within the plan's history, props and closes parsed as ParlayAPI really sends them (NHL props added); Check odds now keeps going when CNO's pages don't list a bet, and ParlayAPI's books price what CNO can't with CNO's own check; an X on every +EV bet removes it for good, with Undo and Put back

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  8d5bb257 ckpt 2162: RESEARCH §45 written (real-key findings, docs pass, backup, features to off
  e877fe78 ckpt 2161: I5 docs pass fixes: include_live on ParlayAPI /odds when live is on, closing
  400540ff ckpt 2160: K1-K2 done: Check odds now tells 'CNO didn't answer' from 'page doesn't list
  ece79c95 ckpt 2159: J1-J2 done: +EV ✕ with Undo and Put back (FeedRemoveTest 3 green)
  8d36a404 ckpt 2158: J2 coded: +EV cards get CNO's ✕ (hideOpportunity -> markHidden, same place
  90340774 ckpt 2157: ParlayAPI alternates bought only when no Pinnacle feed is on (marketsFor alt
  1808bb5e ckpt 2156: I4: ParlayAccount reads /v1/usage (credits month, reset Oct 1) with api-key-
  4f81f856 ckpt 2155: Real-key probe findings coded (ParlayMarkets normalizer, NHL props, flat clo
  36f64899 ckpt 2154: Recorded Tj's key-sharing request as I1-I3 (key kept in scratchpad only, mas
  3d70cba6 ckpt 2153: v0.28.0 (code 56) released and recorded; H1-H6 ticked
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
