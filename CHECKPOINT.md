# CHECKPOINT 3981 — read me first, then TASKS.md

**Written:** 2026-10-22T13:31:22Z · **tests:** all 9 fast checks green
**Branch:** `claude/optimistic-curie-qrfdgy` · **builds on:** `f0438fb78` (this checkpoint is the commit after it)

## Just done
pre-release: CH58: live SharpAPI feed reads ATP/WTA tennis; live lane note with feed/Novig/matched counts; live feed and live bids on (paper) (versionCode 388, v1.0.48)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.48), then run: bash tools/record-release.sh v1.0.48 388 "CH58: live SharpAPI feed reads ATP/WTA tennis; live lane note with feed/Novig/matched counts; live feed and live bids on (paper)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  283ea08f9 ckpt 3980: ANALYST 2026-10-22: no rule change, research card posted
  618ac4373 ckpt 3979: CH58b tennis forced into live feed; engine down until hours
  4fa188354 ckpt 3978: live SharpAPI feed reads ATP/WTA whenever live tennis is on (the phone's co
  5b2bc391b ckpt 3977: lane note carries the feed's own note (store freshness, per-league live Pin
  6d4c78d8b ckpt 3976: CH58 written
  6e6b0dde1 ckpt 3975: data tests green
  94fa3f2e0 ckpt 3974: data tests passed
  5f3245740 ckpt 3973: live lane note shows feed/live/Novig/matched/lines counts (compiles)
  4bca336f1 ckpt 3972: pinnLive+liveBid on (paper)
  40695f29c ckpt 3971: live feed -> SHARPAPI
```
