# CHECKPOINT 3248 — read me first, then TASKS.md

**Written:** 2026-10-14T18:22:31Z · **tests:** all 4 fast checks green
**Branch:** `ccr-5fc41785-3m8rjr` · **builds on:** `1093b87e9` (this checkpoint is the commit after it)

## Just done
pre-release: v0.99.11: the last wallet-filling pass bids every start time and kind with no sharp book needed (popular first, riskier kinds at a higher edge), and bids read ten days ahead (versionCode 270, v0.99.11)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.99.11), then run: bash tools/record-release.sh v0.99.11 270 "v0.99.11: the last wallet-filling pass bids every start time and kind with no sharp book needed (popular first, riskier kinds at a higher edge), and bids read ten days ahead"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  1093b87e9 ckpt 3247: pre-ship: v0.99.11: the last wallet-filling pass bids every start time and 
  8afb8b263 ckpt 3246: pre-ship: v0.99.11: the last wallet-filling pass bids every start time and 
  2b98e8d05 ckpt 3245: v0.99.9 recorded; v0.99.10 shipped (wallet-filling bids ignore max-behind, 
  1d5882246 ckpt 3244: pre-release: v0.99.10: wallet-filling bids may rest far behind the best bid
  17c56d7d7 ckpt 3243: pre-ship: v0.99.10: wallet-filling bids may rest far behind the best bid, a
  767ab0abe ckpt 3242: TWS: logged Tj's 'few bids after a full scan' report; no v0.99.8 diagnostic
  4e433b62b ckpt 3241: v0.99.9 shipped (TNR1 requote during scan), release.yml triggered
  1fdb09d4d ckpt 3240: pre-release: v0.99.9: the +EV list re-prices its bets from Novig's live pri
  d8aae8d11 ckpt 3239: pre-ship: v0.99.9: the +EV list re-prices its bets from Novig's live prices
  ae43c57f5 ckpt 3238: v0.99.8 released and recorded (TWR2 done)
```
