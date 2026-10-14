# CHECKPOINT 3240 — read me first, then TASKS.md

**Written:** 2026-10-14T18:04:57Z · **tests:** all 4 fast checks green
**Branch:** `ccr-5fc41785-3m8rjr` · **builds on:** `d8aae8d11` (this checkpoint is the commit after it)

## Just done
pre-release: v0.99.9: the +EV list re-prices its bets from Novig's live prices every 15 s even while a scan runs, so a bet that turned negative EV leaves the list (versionCode 268, v0.99.9)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.99.9), then run: bash tools/record-release.sh v0.99.9 268 "v0.99.9: the +EV list re-prices its bets from Novig's live prices every 15 s even while a scan runs, so a bet that turned negative EV leaves the list"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  d8aae8d11 ckpt 3239: pre-ship: v0.99.9: the +EV list re-prices its bets from Novig's live prices
  ae43c57f5 ckpt 3238: v0.99.8 released and recorded (TWR2 done)
  4702f8911 ckpt 3237: v0.99.8 shipped, release.yml triggered
  173d62fd8 ckpt 3236: pre-release: v0.99.8: the auto bids read a week of games, so the weekend's 
  b99774136 ckpt 3235: pre-ship: v0.99.8: the auto bids read a week of games, so the weekend's NFL
  dddadc69a ckpt 3234: v0.99.7 recorded; week-ahead scan reach for bids done + MakerTest fixed (TW
  fbc986247 ckpt 3233: pre-release: v0.99.7: scan errors about Novig (the key stand-down, a failed
  91e37bbe4 ckpt 3232: pre-ship: v0.99.7: scan errors about Novig (the key stand-down, a failed re
  d8e01c380 ckpt 3231: pre-ship: v0.99.7: scan errors about Novig (the key stand-down, a failed re
  7d12c39cd ckpt 3230: pre-release: v0.99.6: Settings > Safeguards (every protection with a switch
```
