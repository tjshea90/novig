# CHECKPOINT 3153 — read me first, then TASKS.md

**Written:** 2026-10-13T18:17:01Z · **tests:** all 4 fast checks green
**Branch:** `ccr-18ecef04-mledpg` · **builds on:** `6b423bd61` (this checkpoint is the commit after it)

## Just done
pre-release: v0.96.0: More, smaller bids (Bids slider switch), bets leave the list when the slip closes, dead bets drop out of the CNO list (versionCode 247, v0.96.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.96.0), then run: bash tools/record-release.sh v0.96.0 247 "v0.96.0: More, smaller bids (Bids slider switch), bets leave the list when the slip closes, dead bets drop out of the CNO list"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6b423bd61 ckpt 3152: pre-ship: v0.96.0: More, smaller bids (Bids slider switch), bets leave the 
  c5b286af0 ckpt 3151: pre-ship: v0.96.0: More, smaller bids (Bids slider switch), bets leave the 
  8bf749d75 ckpt 3150: pre-ship: v0.96.0: More, smaller bids (Bids slider switch), bets leave the 
  348b730f0 ckpt 3149: TMB3: provisional hide + restore after miss, tested; TMB4 code (cnoShown -E
  85f752848 ckpt 3148: TMB1-2: BidTier + many-bids mode (data layer, UI switch) tested
  a076e6875 ckpt 3147: TMB tasks written
  c5456c74a ckpt 3146: answered Pinnacle-steam question; TSM1/TSM2 written
  781eb5e0c ckpt 3145: sample-size note
  88ed6cc22 ckpt 3144: full 1648-record refresh of profitable types / team totals
  4e0dcb2af ckpt 3143: team totals detail + correction (README row pools bets and bids)
```
