# CHECKPOINT 2150 — read me first, then TASKS.md

**Written:** 2026-09-30T03:34:03Z · **tests:** all 3 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `3c72f0fb` (this checkpoint is the commit after it)

## Just done
H1-D Runway counts pinnapi behind PinnWire (RunwayTest); H5 maxAgeSec=600 on /props, commenceTimeTo=window+24h on ParlayAPI /odds (requestKey incl. window); best-practice degraded-mode guard ParlaySourceQuality (free, stale/missing/breach-past-stale books left out); ParlayAccountTest 9

## Do this next
full floor; RESEARCH §44; version 0.28.0 code 56; ship; answer Tj (report findings, fixes, what I need: a dedicated second ParlayAPI key to verify shapes)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  323fcb36 ckpt 2149: H6 in: CreditHeaders (x-requests/x-credits/X-RateLimit-* monthly only, reset
  9a87d0a8 ckpt 2148: H1-C fixed: closes reopened when a new close source is active (CloseSource.i
  69e64611 ckpt 2147: H1-B fixed: CNO pause reason kept (CnoState.lastPause/At, pauseFor), Check o
  97657671 ckpt 2146: Pacing bugs fixed (stale out-of-order answers ignored, STALE_WINDOW/SLACK; f
  4901bda3 ckpt 2145: Recorded Tj's diagnostics-review request as H1-H3
  18e92257 ckpt 2144: v0.27.0 (code 55) released and recorded (CI 36659840921 + release.yml 366601
  07ce7847 ckpt 2143: pre-release: v0.27.0: ParlayAPI fully used on its $5 Starter plan: Pinnacle'
  c5b40f47 ckpt 2142: pre-ship: v0.27.0: ParlayAPI fully used on its $5 Starter plan: Pinnacle's c
  2e0a6863 ckpt 2141: Full test findings #2-#3 fixed: CLV card copy now names Pinnacle's ParlayAPI
  ebc4a263 ckpt 2140: Full test finding #1 fixed: background auto-scans could spend ParlayAPI's wh
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
