# CHECKPOINT 2831 — read me first, then TASKS.md

**Written:** 2026-10-10T05:26:37Z · **tests:** all 4 fast checks green
**Branch:** `ccr-bab5067e-obkelv` · **builds on:** `d961dee1` (this checkpoint is the commit after it)

## Just done
pre-release: v0.85.1: live bids say why none is up; live orders may pay up to the minimum edge (versionCode 161, v0.85.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.85.1), then run: bash tools/record-release.sh v0.85.1 161 "v0.85.1: live bids say why none is up; live orders may pay up to the minimum edge"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  d961dee1 ckpt 2830: pre-ship: v0.85.1: live bids say why none is up; live orders may pay up to t
  d4955434 ckpt 2829: v0.85.0 released (Release page live, BUILDLOG recorded); SW5/SW6 ticked
  f03ebd2b ckpt 2828: pre-release: v0.85.0: live bids on Novig (Settings › Live bids) (versionCo
  54f3266b ckpt 2827: SW done pending ship: RESEARCH §124, NOVIG_API §23, grid output saved, pre
  7d9173d9 ckpt 2826: SW3 hardening done: desk refresh clamp (min(refresh, ttl/2)) + test; runaway
  f57da316 ckpt 2825: SW4: app wiring (VigilantApp desk+orders adapter+pinnTick, KillSwitch, LiveF
  4ebb1223 ckpt 2824: SW2/SW3: data/livebid done + 54 tests green (LiveBidJudgeTest, LiveBidPreset
  7290f89d ckpt 2823: SW2-SW3 in progress: data/livebid/ (LiveBidRules+presets Careful/Balanced/Pa
  8b61f214 ckpt 2822: SW1: logged Tj's live-bid build request (TASKS.md SW)
  32ada719 ckpt 2821: SV done: investigation of live betting on Novig. RESEARCH.md §123 + NOVIG_A
```
