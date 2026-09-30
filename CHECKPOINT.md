# CHECKPOINT 2149 — read me first, then TASKS.md

**Written:** 2026-09-30T03:30:30Z · **tests:** all 3 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `289eb940` (this checkpoint is the commit after it)

## Just done
H6 in: CreditHeaders (x-requests/x-credits/X-RateLimit-* monthly only, reset epoch, request id), KeyUsage.resetAtMs (meter/pace/runway/views follow the provider's reset), recordBalance; ParlayAccount (free /v1/meta/api-key-check, tolerant parse, refresh on scan start/usage tab/Diagnostics/key add); X-API-Key header for ParlayAPI, one retry on 502-504/IO, request id in errors; ParlayAccountTest 7 green

## Do this next
H1-D Runway PinnWire+pinnapi; H5 maxAgeSec on /props + commenceTimeTo on /odds; source-quality guard (maybe); full floor; RESEARCH §44; ship; answer (what I need: a dedicated ParlayAPI key to verify shapes)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  9a87d0a8 ckpt 2148: H1-C fixed: closes reopened when a new close source is active (CloseSource.i
  69e64611 ckpt 2147: H1-B fixed: CNO pause reason kept (CnoState.lastPause/At, pauseFor), Check o
  97657671 ckpt 2146: Pacing bugs fixed (stale out-of-order answers ignored, STALE_WINDOW/SLACK; f
  4901bda3 ckpt 2145: Recorded Tj's diagnostics-review request as H1-H3
  18e92257 ckpt 2144: v0.27.0 (code 55) released and recorded (CI 36659840921 + release.yml 366601
  07ce7847 ckpt 2143: pre-release: v0.27.0: ParlayAPI fully used on its $5 Starter plan: Pinnacle'
  c5b40f47 ckpt 2142: pre-ship: v0.27.0: ParlayAPI fully used on its $5 Starter plan: Pinnacle's c
  2e0a6863 ckpt 2141: Full test findings #2-#3 fixed: CLV card copy now names Pinnacle's ParlayAPI
  ebc4a263 ckpt 2140: Full test finding #1 fixed: background auto-scans could spend ParlayAPI's wh
  fda54162 ckpt 2139: RESEARCH.md §43 written (5 sources, ParlayAPI tested endpoints/costs, what 
```

(8 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
