# CHECKPOINT 2151 — read me first, then TASKS.md

**Written:** 2026-09-30T03:36:28Z · **tests:** all 3 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `ece925eb` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.28.0: ParlayAPI fixes from Tj's diagnostics and its best practices: the key's own credit count in the meter (free check, exact reset time), scans no longer held back by a late reply, a new plan paced from its first day, key sent in a header, one retry on a blip, stale books left out, props and games trimmed to what can price; CNO's pause reason kept; closes re-looked for with ParlayAPI; PinnWire's runway counts pinnapi; a key pasted in chat is masked

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  5cfff92b ckpt 2150: H1-D Runway counts pinnapi behind PinnWire (RunwayTest); H5 maxAgeSec=600 on
  323fcb36 ckpt 2149: H6 in: CreditHeaders (x-requests/x-credits/X-RateLimit-* monthly only, reset
  9a87d0a8 ckpt 2148: H1-C fixed: closes reopened when a new close source is active (CloseSource.i
  69e64611 ckpt 2147: H1-B fixed: CNO pause reason kept (CnoState.lastPause/At, pauseFor), Check o
  97657671 ckpt 2146: Pacing bugs fixed (stale out-of-order answers ignored, STALE_WINDOW/SLACK; f
  4901bda3 ckpt 2145: Recorded Tj's diagnostics-review request as H1-H3
  18e92257 ckpt 2144: v0.27.0 (code 55) released and recorded (CI 36659840921 + release.yml 366601
  07ce7847 ckpt 2143: pre-release: v0.27.0: ParlayAPI fully used on its $5 Starter plan: Pinnacle'
  c5b40f47 ckpt 2142: pre-ship: v0.27.0: ParlayAPI fully used on its $5 Starter plan: Pinnacle's c
  2e0a6863 ckpt 2141: Full test findings #2-#3 fixed: CLV card copy now names Pinnacle's ParlayAPI
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
