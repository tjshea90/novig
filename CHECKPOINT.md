# CHECKPOINT 2153 — read me first, then TASKS.md

**Written:** 2026-09-30T03:47:36Z · **tests:** all 3 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `0228cb12` (this checkpoint is the commit after it)

## Just done
v0.28.0 (code 56) released and recorded; H1-H6 ticked

## Do this next
When Tj shares a dedicated ParlayAPI key (redacted in INBOX by capture hook): export it only as an env var in the container (never a file in the repo), call /v1/meta/api-key-check, /v1/meta/usage, one /v1/sports/americanfootball_nfl/props, one /v1/historical/closing-lines.json and one /v1/sports/.../closing-lines; fix ParlayAccount/ParlayProps/ParlayCloses field names to match; ship; tell Tj to delete that key

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  c974b2a9 ckpt 2152: pre-release: v0.28.0: ParlayAPI fixes from Tj's diagnostics and its best pra
  2661b457 ckpt 2151: pre-ship: v0.28.0: ParlayAPI fixes from Tj's diagnostics and its best practi
  5cfff92b ckpt 2150: H1-D Runway counts pinnapi behind PinnWire (RunwayTest); H5 maxAgeSec=600 on
  323fcb36 ckpt 2149: H6 in: CreditHeaders (x-requests/x-credits/X-RateLimit-* monthly only, reset
  9a87d0a8 ckpt 2148: H1-C fixed: closes reopened when a new close source is active (CloseSource.i
  69e64611 ckpt 2147: H1-B fixed: CNO pause reason kept (CnoState.lastPause/At, pauseFor), Check o
  97657671 ckpt 2146: Pacing bugs fixed (stale out-of-order answers ignored, STALE_WINDOW/SLACK; f
  4901bda3 ckpt 2145: Recorded Tj's diagnostics-review request as H1-H3
  18e92257 ckpt 2144: v0.27.0 (code 55) released and recorded (CI 36659840921 + release.yml 366601
  07ce7847 ckpt 2143: pre-release: v0.27.0: ParlayAPI fully used on its $5 Starter plan: Pinnacle'
```
