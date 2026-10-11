# CHECKPOINT 2938 — read me first, then TASKS.md

**Written:** 2026-10-11T05:27:23Z · **tests:** all 4 fast checks green
**Branch:** `claude/unabated-oddsapi-integration-1u6kdc` · **builds on:** `2bed34f6` (this checkpoint is the commit after it)

## Just done
pre-release: v0.89.9: live sniper, one switch for taker, tail bettor, cover taker and bids at the slider position (versionCode 194, v0.89.9)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.89.9), then run: bash tools/record-release.sh v0.89.9 194 "v0.89.9: live sniper, one switch for taker, tail bettor, cover taker and bids at the slider position"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  2bed34f6 ckpt 2937: TF: Live sniper (LiveSniper = autopilot + cover taker + slider-level bids), 
  23d442f3 ckpt 2936: pre-release: v0.89.8: OddsPapi Free no longer errors on empty fixture window
  08a2cec0 ckpt 2935: TE/TD: OddsPapi Free 404 FIXTURE_NOT_FOUND = empty window/no odds (not a fai
  3a19cb83 ckpt 2934: TE: OddsPapi Free fair-odds feed now on by default (10/day, schema 16 migrat
  7244f0ee ckpt 2933: pre-release: v0.89.6: live bids Safety/Volume slider (one control sets margi
  3c07f0a9 ckpt 2932: TD: Safety/Volume slider (LiveBidSlider + LiveBidSliderTest, desk evAtFill +
  cbd0b033 ckpt 2931: pre-release: v0.89.5: bid guard (cancels resting orders with no expiry after
  8796d96e ckpt 2930: pre-ship: v0.89.5: bid guard (cancels resting orders with no expiry after 30
  7a0104c1 ckpt 2929: pre-ship: v0.89.5: bid guard (cancels resting orders with no expiry after 30
  fb078545 ckpt 2928: pre-release: v0.89.4: tennis bids (plain rules for ATP/WTA, lean scan keeps 
```
