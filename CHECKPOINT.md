# CHECKPOINT 2826 — read me first, then TASKS.md

**Written:** 2026-10-10T04:34:10Z · **tests:** all 4 fast checks green
**Branch:** `ccr-bab5067e-obkelv` · **builds on:** `650f6aa7` (this checkpoint is the commit after it)

## Just done
SW3 hardening done: desk refresh clamp (min(refresh, ttl/2)) + test; runaway-post breaker, unexplained cool-off, late-fill audit, otherDesks; 71 data tests green

## Do this next
Run the whole floor (bash tools/test.sh), fix fallout; screenshots of Settings > Live bids; docs (RESEARCH 124, NOVIG_API, TASKS tick SW1-6); version bump + BUILDLOG; ship.sh; release.yml; send Tj the link

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  f57da316 ckpt 2825: SW4: app wiring (VigilantApp desk+orders adapter+pinnTick, KillSwitch, LiveF
  4ebb1223 ckpt 2824: SW2/SW3: data/livebid done + 54 tests green (LiveBidJudgeTest, LiveBidPreset
  7290f89d ckpt 2823: SW2-SW3 in progress: data/livebid/ (LiveBidRules+presets Careful/Balanced/Pa
  8b61f214 ckpt 2822: SW1: logged Tj's live-bid build request (TASKS.md SW)
  32ada719 ckpt 2821: SV done: investigation of live betting on Novig. RESEARCH.md §123 + NOVIG_A
  91487752 ckpt 2820: SV1: logged Tj's live-betting investigation request (TASKS.md SV); read RESE
  6b159526 ckpt 2819: SU1-SU3 done: 5 UI design candidates (Desk, Expressive, Signal, Daylight, Ne
  569228d6 ckpt 2818: SU1 in progress: baseline Roborazzi render works locally (app/screenshots/1_
  80722a11 ckpt 2817: logged Tj's UI design-candidates request as TASKS.md SU
  51247364 ckpt 2816: Added Tj's standing setup rules (2026-10-10: use plugins/skills/connectors, 
```

(11 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
