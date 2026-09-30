# CHECKPOINT 2156 — read me first, then TASKS.md

**Written:** 2026-09-30T04:31:29Z · **tests:** all 3 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `a28f811d` (this checkpoint is the commit after it)

## Just done
I4: ParlayAccount reads /v1/usage (credits month, reset Oct 1) with api-key-check fallback, REFRESH 60s, stale guard; CreditPace spreads over the days left; ParlayClosesTest on real shapes (33 green). J1-J3 recorded

## Do this next
Drop alternates from ParlayAPI /odds (marketsFor, texts), then J1-J2 (+EV X like CNO's), I5 docs pass, floor, ship v0.29.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  4f81f856 ckpt 2155: Real-key probe findings coded (ParlayMarkets normalizer, NHL props, flat clo
  36f64899 ckpt 2154: Recorded Tj's key-sharing request as I1-I3 (key kept in scratchpad only, mas
  3d70cba6 ckpt 2153: v0.28.0 (code 56) released and recorded; H1-H6 ticked
  c974b2a9 ckpt 2152: pre-release: v0.28.0: ParlayAPI fixes from Tj's diagnostics and its best pra
  2661b457 ckpt 2151: pre-ship: v0.28.0: ParlayAPI fixes from Tj's diagnostics and its best practi
  5cfff92b ckpt 2150: H1-D Runway counts pinnapi behind PinnWire (RunwayTest); H5 maxAgeSec=600 on
  323fcb36 ckpt 2149: H6 in: CreditHeaders (x-requests/x-credits/X-RateLimit-* monthly only, reset
  9a87d0a8 ckpt 2148: H1-C fixed: closes reopened when a new close source is active (CloseSource.i
  69e64611 ckpt 2147: H1-B fixed: CNO pause reason kept (CnoState.lastPause/At, pauseFor), Check o
  97657671 ckpt 2146: Pacing bugs fixed (stale out-of-order answers ignored, STALE_WINDOW/SLACK; f
```

(13 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
