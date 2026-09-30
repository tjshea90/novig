# CHECKPOINT 2158 — read me first, then TASKS.md

**Written:** 2026-09-30T04:35:29Z · **tests:** all 3 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `184a6cd3` (this checkpoint is the commit after it)

## Just done
J2 coded: +EV cards get CNO's ✕ (hideOpportunity -> markHidden, same placed.json record), snackbar Undo, 'Show the N bets you removed' with Put back; compiles. K1-K3 recorded (Check odds now: CNO didn't answer)

## Do this next
J2 tests (UI test ✕ hides + removed list; VM/state test feed hides a hidden opp), then K1 investigate BetRecheck CNO failure, K2 ParlayAPI backup, I5, floor, ship v0.29.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  90340774 ckpt 2157: ParlayAPI alternates bought only when no Pinnacle feed is on (marketsFor alt
  1808bb5e ckpt 2156: I4: ParlayAccount reads /v1/usage (credits month, reset Oct 1) with api-key-
  4f81f856 ckpt 2155: Real-key probe findings coded (ParlayMarkets normalizer, NHL props, flat clo
  36f64899 ckpt 2154: Recorded Tj's key-sharing request as I1-I3 (key kept in scratchpad only, mas
  3d70cba6 ckpt 2153: v0.28.0 (code 56) released and recorded; H1-H6 ticked
  c974b2a9 ckpt 2152: pre-release: v0.28.0: ParlayAPI fixes from Tj's diagnostics and its best pra
  2661b457 ckpt 2151: pre-ship: v0.28.0: ParlayAPI fixes from Tj's diagnostics and its best practi
  5cfff92b ckpt 2150: H1-D Runway counts pinnapi behind PinnWire (RunwayTest); H5 maxAgeSec=600 on
  323fcb36 ckpt 2149: H6 in: CreditHeaders (x-requests/x-credits/X-RateLimit-* monthly only, reset
  9a87d0a8 ckpt 2148: H1-C fixed: closes reopened when a new close source is active (CloseSource.i
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
