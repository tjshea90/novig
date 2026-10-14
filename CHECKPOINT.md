# CHECKPOINT 3230 — read me first, then TASKS.md

**Written:** 2026-10-14T14:12:03Z · **tests:** all 4 fast checks green
**Branch:** `ccr-147419fb-llvuq5` · **builds on:** `4d583c2dc` (this checkpoint is the commit after it)

## Just done
pre-release: v0.99.6: Settings > Safeguards (every protection with a switch and one button for all), a Live tab with every live feature (Live light, live games, sockets, the master switch), and wallet-filling bids that reach games later in the week at a wider margin when nothing is left in the usual window (versionCode 265, v0.99.6)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.99.6), then run: bash tools/record-release.sh v0.99.6 265 "v0.99.6: Settings > Safeguards (every protection with a switch and one button for all), a Live tab with every live feature (Live light, live games, sockets, the master switch), and wallet-filling bids that reach games later in the week at a wider margin when nothing is left in the usual window"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  4d583c2dc ckpt 3229: pre-ship: v0.99.6: Settings > Safeguards (every protection with a switch an
  9707921f0 ckpt 3228: TLV1/TLV2: Safeguards page + Live tab built (tests green)
  f290d54a0 ckpt 3227: v0.99.5 shipped (live master switch, Live light page, by-reference state, t
  01155a981 ckpt 3226: pre-release: v0.99.5: one Live features master switch (every live lane and 
  df83b480a ckpt 3225: pre-ship: v0.99.5: one Live features master switch (every live lane and soc
  e1618641f ckpt 3224: pre-release: v0.99.4: player-prop fair prices cut (Overs 7 points, Unders 3
  3826fa353 ckpt 3223: pre-ship: v0.99.4: player-prop fair prices cut (Overs 7 points, Unders 3: t
  0b3aacb2a ckpt 3222: bet history 2026-10-14 read: longshot/prop EV overstated; profit_profile.js
  c323738a3 ckpt 3221: pre-release: v0.99.3: Live light loosened (model fair 90%, bids 7% under th
  debd9aea9 ckpt 3220: pre-ship: v0.99.3: Live light loosened (model fair 90%, bids 7% under the f
```
