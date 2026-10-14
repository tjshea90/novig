# CHECKPOINT 3233 — read me first, then TASKS.md

**Written:** 2026-10-14T17:08:04Z · **tests:** all 4 fast checks green
**Branch:** `ccr-147419fb-llvuq5` · **builds on:** `91e37bbe4` (this checkpoint is the commit after it)

## Just done
pre-release: v0.99.7: scan errors about Novig (the key stand-down, a failed read) leave the screen by themselves once they stop applying, and a second, deeper bid goes up on sides that already have one while the wallet is under its target (versionCode 266, v0.99.7)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.99.7), then run: bash tools/record-release.sh v0.99.7 266 "v0.99.7: scan errors about Novig (the key stand-down, a failed read) leave the screen by themselves once they stop applying, and a second, deeper bid goes up on sides that already have one while the wallet is under its target"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  91e37bbe4 ckpt 3232: pre-ship: v0.99.7: scan errors about Novig (the key stand-down, a failed re
  d8e01c380 ckpt 3231: pre-ship: v0.99.7: scan errors about Novig (the key stand-down, a failed re
  7d12c39cd ckpt 3230: pre-release: v0.99.6: Settings > Safeguards (every protection with a switch
  4d583c2dc ckpt 3229: pre-ship: v0.99.6: Settings > Safeguards (every protection with a switch an
  9707921f0 ckpt 3228: TLV1/TLV2: Safeguards page + Live tab built (tests green)
  f290d54a0 ckpt 3227: v0.99.5 shipped (live master switch, Live light page, by-reference state, t
  01155a981 ckpt 3226: pre-release: v0.99.5: one Live features master switch (every live lane and 
  df83b480a ckpt 3225: pre-ship: v0.99.5: one Live features master switch (every live lane and soc
  e1618641f ckpt 3224: pre-release: v0.99.4: player-prop fair prices cut (Overs 7 points, Unders 3
  3826fa353 ckpt 3223: pre-ship: v0.99.4: player-prop fair prices cut (Overs 7 points, Unders 3: t
```
