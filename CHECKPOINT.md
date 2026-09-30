# CHECKPOINT 2152 — read me first, then TASKS.md

**Written:** 2026-09-30T03:37:29Z · **tests:** all 3 fast checks green
**Branch:** `ccr-5babcffd-eylneu` · **builds on:** `2661b457` (this checkpoint is the commit after it)

## Just done
pre-release: v0.28.0: ParlayAPI fixes from Tj's diagnostics and its best practices: the key's own credit count in the meter (free check, exact reset time), scans no longer held back by a late reply, a new plan paced from its first day, key sent in a header, one retry on a blip, stale books left out, props and games trimmed to what can price; CNO's pause reason kept; closes re-looked for with ParlayAPI; PinnWire's runway counts pinnapi; a key pasted in chat is masked (versionCode 56, v0.28.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.28.0), then run: bash tools/record-release.sh v0.28.0 56 "v0.28.0: ParlayAPI fixes from Tj's diagnostics and its best practices: the key's own credit count in the meter (free check, exact reset time), scans no longer held back by a late reply, a new plan paced from its first day, key sent in a header, one retry on a blip, stale books left out, props and games trimmed to what can price; CNO's pause reason kept; closes re-looked for with ParlayAPI; PinnWire's runway counts pinnapi; a key pasted in chat is masked"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  2661b457 ckpt 2151: pre-ship: v0.28.0: ParlayAPI fixes from Tj's diagnostics and its best practi
  5cfff92b ckpt 2150: H1-D Runway counts pinnapi behind PinnWire (RunwayTest); H5 maxAgeSec=600 on
  323fcb36 ckpt 2149: H6 in: CreditHeaders (x-requests/x-credits/X-RateLimit-* monthly only, reset
  9a87d0a8 ckpt 2148: H1-C fixed: closes reopened when a new close source is active (CloseSource.i
  69e64611 ckpt 2147: H1-B fixed: CNO pause reason kept (CnoState.lastPause/At, pauseFor), Check o
  97657671 ckpt 2146: Pacing bugs fixed (stale out-of-order answers ignored, STALE_WINDOW/SLACK; f
  4901bda3 ckpt 2145: Recorded Tj's diagnostics-review request as H1-H3
  18e92257 ckpt 2144: v0.27.0 (code 55) released and recorded (CI 36659840921 + release.yml 366601
  07ce7847 ckpt 2143: pre-release: v0.27.0: ParlayAPI fully used on its $5 Starter plan: Pinnacle'
  c5b40f47 ckpt 2142: pre-ship: v0.27.0: ParlayAPI fully used on its $5 Starter plan: Pinnacle's c
```
