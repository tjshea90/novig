# CHECKPOINT 2834 — read me first, then TASKS.md

**Written:** 2026-10-10T06:19:59Z · **tests:** all 4 fast checks green
**Branch:** `ccr-bab5067e-obkelv` · **builds on:** `5fe9b0bd` (this checkpoint is the commit after it)

## Just done
pre-release: v0.85.2: live bid engine survives a bad message and says why it is not judging (versionCode 162, v0.85.2)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.85.2), then run: bash tools/record-release.sh v0.85.2 162 "v0.85.2: live bid engine survives a bad message and says why it is not judging"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  5fe9b0bd ckpt 2833: pre-ship: v0.85.2: live bid engine survives a bad message and says why it is
  d5a67be7 ckpt 2832: v0.85.1 released and recorded
  6be33f9c ckpt 2831: pre-release: v0.85.1: live bids say why none is up; live orders may pay up t
  d961dee1 ckpt 2830: pre-ship: v0.85.1: live bids say why none is up; live orders may pay up to t
  d4955434 ckpt 2829: v0.85.0 released (Release page live, BUILDLOG recorded); SW5/SW6 ticked
  f03ebd2b ckpt 2828: pre-release: v0.85.0: live bids on Novig (Settings › Live bids) (versionCo
  54f3266b ckpt 2827: SW done pending ship: RESEARCH §124, NOVIG_API §23, grid output saved, pre
  7d9173d9 ckpt 2826: SW3 hardening done: desk refresh clamp (min(refresh, ttl/2)) + test; runaway
  f57da316 ckpt 2825: SW4: app wiring (VigilantApp desk+orders adapter+pinnTick, KillSwitch, LiveF
  4ebb1223 ckpt 2824: SW2/SW3: data/livebid done + 54 tests green (LiveBidJudgeTest, LiveBidPreset
```
