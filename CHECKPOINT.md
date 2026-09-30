# CHECKPOINT 2168 — read me first, then TASKS.md

**Written:** 2026-09-30T05:30:59Z · **tests:** all 3 fast checks green
**Branch:** `ccr-01f0c1f9-xy73ag` · **builds on:** `b551b92c` (this checkpoint is the commit after it)

## Just done
M1 data: Injury/InjuryIndex/ParlayInjuries (/props rows' injury free, /injuries 1 cr per sport per 10 min), TheOddsApiClient.parlayGet (generic metered ParlayAPI call, body credits, 503 to caller); ParlayInjuriesTest 7 green

## Do this next
M1 app: VigilantApp wiring (index into parlayProps/background/ParlayBooks, ParlayInjuries), VM tag map for feed/CNO/tracker + /injuries fill for uncovered, InjuryTag composable on +EV card+sheet, CNO card, widget, Tracker open bets; UI test

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  7742a805 ckpt 2167: Handoff for Tj's next session: PARLAY_API.md (permanent ParlayAPI memory + �
  36843185 ckpt 2166: v0.29.0 (code 57) released and recorded; I3/J3/K3 ticked
  597ed3f4 ckpt 2165: v0.29.0 gated and pushed (1117 tests green); I1,I2,I4-I6 ticked
  1c2128d8 ckpt 2164: pre-release: v0.29.0: ParlayAPI matched to its docs and Tj's real key: the m
  64fb7853 ckpt 2163: pre-ship: v0.29.0: ParlayAPI matched to its docs and Tj's real key: the mete
  8d5bb257 ckpt 2162: RESEARCH §45 written (real-key findings, docs pass, backup, features to off
  e877fe78 ckpt 2161: I5 docs pass fixes: include_live on ParlayAPI /odds when live is on, closing
  400540ff ckpt 2160: K1-K2 done: Check odds now tells 'CNO didn't answer' from 'page doesn't list
  ece79c95 ckpt 2159: J1-J2 done: +EV ✕ with Undo and Put back (FeedRemoveTest 3 green)
  8d36a404 ckpt 2158: J2 coded: +EV cards get CNO's ✕ (hideOpportunity -> markHidden, same place
```

(11 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
