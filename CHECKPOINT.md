# CHECKPOINT 3236 — read me first, then TASKS.md

**Written:** 2026-10-14T17:43:37Z · **tests:** all 4 fast checks green
**Branch:** `ccr-5fc41785-3m8rjr` · **builds on:** `b99774136` (this checkpoint is the commit after it)

## Just done
pre-release: v0.99.8: the auto bids read a week of games, so the weekend's NFL and college slate is priced and the wallet can fill (the Starts within chip no longer shortens the scan) (versionCode 267, v0.99.8)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.99.8), then run: bash tools/record-release.sh v0.99.8 267 "v0.99.8: the auto bids read a week of games, so the weekend's NFL and college slate is priced and the wallet can fill (the Starts within chip no longer shortens the scan)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  b99774136 ckpt 3235: pre-ship: v0.99.8: the auto bids read a week of games, so the weekend's NFL
  dddadc69a ckpt 3234: v0.99.7 recorded; week-ahead scan reach for bids done + MakerTest fixed (TW
  fbc986247 ckpt 3233: pre-release: v0.99.7: scan errors about Novig (the key stand-down, a failed
  91e37bbe4 ckpt 3232: pre-ship: v0.99.7: scan errors about Novig (the key stand-down, a failed re
  d8e01c380 ckpt 3231: pre-ship: v0.99.7: scan errors about Novig (the key stand-down, a failed re
  7d12c39cd ckpt 3230: pre-release: v0.99.6: Settings > Safeguards (every protection with a switch
  4d583c2dc ckpt 3229: pre-ship: v0.99.6: Settings > Safeguards (every protection with a switch an
  9707921f0 ckpt 3228: TLV1/TLV2: Safeguards page + Live tab built (tests green)
  f290d54a0 ckpt 3227: v0.99.5 shipped (live master switch, Live light page, by-reference state, t
  01155a981 ckpt 3226: pre-release: v0.99.5: one Live features master switch (every live lane and 
```
