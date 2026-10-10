# CHECKPOINT 2827 — read me first, then TASKS.md

**Written:** 2026-10-10T04:41:38Z · **tests:** all 4 fast checks green
**Branch:** `ccr-bab5067e-obkelv` · **builds on:** `4d05e460` (this checkpoint is the commit after it)

## Just done
SW done pending ship: RESEARCH §124, NOVIG_API §23, grid output saved, preset wording corrected to match the grid (flat 3-6%), version 0.85.0 code 160, screenshot of Settings > Live bids rendered; full floor green (2933)

## Do this next
Wait for ci.yml green on this commit (mcp__github__actions_list), then bash ship.sh, trigger release.yml, confirm Release, tools/record-release.sh v0.85.0 160, tick SW5/SW6, send Tj the link + the plain-language summary (presets, 1/8 Kelly caps, unverified in play, trial ends 23:34Z)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  7d9173d9 ckpt 2826: SW3 hardening done: desk refresh clamp (min(refresh, ttl/2)) + test; runaway
  f57da316 ckpt 2825: SW4: app wiring (VigilantApp desk+orders adapter+pinnTick, KillSwitch, LiveF
  4ebb1223 ckpt 2824: SW2/SW3: data/livebid done + 54 tests green (LiveBidJudgeTest, LiveBidPreset
  7290f89d ckpt 2823: SW2-SW3 in progress: data/livebid/ (LiveBidRules+presets Careful/Balanced/Pa
  8b61f214 ckpt 2822: SW1: logged Tj's live-bid build request (TASKS.md SW)
  32ada719 ckpt 2821: SV done: investigation of live betting on Novig. RESEARCH.md §123 + NOVIG_A
  91487752 ckpt 2820: SV1: logged Tj's live-betting investigation request (TASKS.md SV); read RESE
  6b159526 ckpt 2819: SU1-SU3 done: 5 UI design candidates (Desk, Expressive, Signal, Daylight, Ne
  569228d6 ckpt 2818: SU1 in progress: baseline Roborazzi render works locally (app/screenshots/1_
  80722a11 ckpt 2817: logged Tj's UI design-candidates request as TASKS.md SU
```

(8 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
