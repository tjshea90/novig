# CHECKPOINT 2833 — read me first, then TASKS.md

**Written:** 2026-10-10T06:16:36Z · **tests:** all 4 fast checks green
**Branch:** `ccr-bab5067e-obkelv` · **builds on:** `aad37b1e` (this checkpoint is the commit after it)

## Just done
pre-ship: v0.85.2: live bid engine survives a bad message and says why it is not judging

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md
     M app/build.gradle.kts
     M app/src/main/kotlin/com/tjshea/vigilant/app/LiveBidText.kt

## Last ten checkpoints
```
  d5a67be7 ckpt 2832: v0.85.1 released and recorded
  6be33f9c ckpt 2831: pre-release: v0.85.1: live bids say why none is up; live orders may pay up t
  d961dee1 ckpt 2830: pre-ship: v0.85.1: live bids say why none is up; live orders may pay up to t
  d4955434 ckpt 2829: v0.85.0 released (Release page live, BUILDLOG recorded); SW5/SW6 ticked
  f03ebd2b ckpt 2828: pre-release: v0.85.0: live bids on Novig (Settings › Live bids) (versionCo
  54f3266b ckpt 2827: SW done pending ship: RESEARCH §124, NOVIG_API §23, grid output saved, pre
  7d9173d9 ckpt 2826: SW3 hardening done: desk refresh clamp (min(refresh, ttl/2)) + test; runaway
  f57da316 ckpt 2825: SW4: app wiring (VigilantApp desk+orders adapter+pinnTick, KillSwitch, LiveF
  4ebb1223 ckpt 2824: SW2/SW3: data/livebid done + 54 tests green (LiveBidJudgeTest, LiveBidPreset
  7290f89d ckpt 2823: SW2-SW3 in progress: data/livebid/ (LiveBidRules+presets Careful/Balanced/Pa
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
