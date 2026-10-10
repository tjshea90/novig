# CHECKPOINT 2824 — read me first, then TASKS.md

**Written:** 2026-10-10T04:14:06Z · **tests:** all 4 fast checks green
**Branch:** `ccr-bab5067e-obkelv` · **builds on:** `af51503f` (this checkpoint is the commit after it)

## Just done
SW2/SW3: data/livebid done + 54 tests green (LiveBidJudgeTest, LiveBidPresetsTest, LiveBidDeskTest); desk fixes: recover, duplicate cancels, PENDING orders not called over

## Do this next
Next: runner integration test (PinnLiveRunnerBidTest), then app wiring (VigilantApp desk+orders adapter+pinnTick+LiveFeedService+KillSwitch), Settings 'Live bids' page, Diagnostics, docs, version, ship

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  7290f89d ckpt 2823: SW2-SW3 in progress: data/livebid/ (LiveBidRules+presets Careful/Balanced/Pa
  8b61f214 ckpt 2822: SW1: logged Tj's live-bid build request (TASKS.md SW)
  32ada719 ckpt 2821: SV done: investigation of live betting on Novig. RESEARCH.md §123 + NOVIG_A
  91487752 ckpt 2820: SV1: logged Tj's live-betting investigation request (TASKS.md SV); read RESE
  6b159526 ckpt 2819: SU1-SU3 done: 5 UI design candidates (Desk, Expressive, Signal, Daylight, Ne
  569228d6 ckpt 2818: SU1 in progress: baseline Roborazzi render works locally (app/screenshots/1_
  80722a11 ckpt 2817: logged Tj's UI design-candidates request as TASKS.md SU
  51247364 ckpt 2816: Added Tj's standing setup rules (2026-10-10: use plugins/skills/connectors, 
  51d99f74 ckpt 2815: pre-release: v0.84.4: Research running foreground service keeps the paper la
  a4effd21 ckpt 2814: pre-ship: v0.84.4: Research running foreground service keeps the paper lab a
```

(8 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
