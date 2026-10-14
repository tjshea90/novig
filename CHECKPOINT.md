# CHECKPOINT 3244 — read me first, then TASKS.md

**Written:** 2026-10-14T18:12:19Z · **tests:** all 4 fast checks green
**Branch:** `ccr-5fc41785-3m8rjr` · **builds on:** `17c56d7d7` (this checkpoint is the commit after it)

## Just done
pre-release: v0.99.10: wallet-filling bids may rest far behind the best bid, and Diagnostics says why the week-ahead pass posted nothing (versionCode 269, v0.99.10)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.99.10), then run: bash tools/record-release.sh v0.99.10 269 "v0.99.10: wallet-filling bids may rest far behind the best bid, and Diagnostics says why the week-ahead pass posted nothing"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  17c56d7d7 ckpt 3243: pre-ship: v0.99.10: wallet-filling bids may rest far behind the best bid, a
  767ab0abe ckpt 3242: TWS: logged Tj's 'few bids after a full scan' report; no v0.99.8 diagnostic
  4e433b62b ckpt 3241: v0.99.9 shipped (TNR1 requote during scan), release.yml triggered
  1fdb09d4d ckpt 3240: pre-release: v0.99.9: the +EV list re-prices its bets from Novig's live pri
  d8aae8d11 ckpt 3239: pre-ship: v0.99.9: the +EV list re-prices its bets from Novig's live prices
  ae43c57f5 ckpt 3238: v0.99.8 released and recorded (TWR2 done)
  4702f8911 ckpt 3237: v0.99.8 shipped, release.yml triggered
  173d62fd8 ckpt 3236: pre-release: v0.99.8: the auto bids read a week of games, so the weekend's 
  b99774136 ckpt 3235: pre-ship: v0.99.8: the auto bids read a week of games, so the weekend's NFL
  dddadc69a ckpt 3234: v0.99.7 recorded; week-ahead scan reach for bids done + MakerTest fixed (TW
```
