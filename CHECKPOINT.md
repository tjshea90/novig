# CHECKPOINT 3652 — read me first, then TASKS.md

**Written:** 2026-10-18T16:42:28Z · **tests:** all 5 fast checks green
**Branch:** `ccr-5a7eb2e9-bn8drh` · **builds on:** `d88a82190` (this checkpoint is the commit after it)

## Just done
pre-release: v0.117.2: PinnWire keys are spent only on PinnWire's own word (its free account read; a per-minute 429 no longer shows a key as used up for the day), not the app's own request count; the engine's bid list stays small enough for the phone to load (versionCode 335, v0.117.2)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.117.2), then run: bash tools/record-release.sh v0.117.2 335 "v0.117.2: PinnWire keys are spent only on PinnWire's own word (its free account read; a per-minute 429 no longer shows a key as used up for the day), not the app's own request count; the engine's bid list stays small enough for the phone to load"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  d88a82190 ckpt 3651: ApiProbe: PinnWire 429 reads window (minute vs day) + retry_after_ms instea
  6479b16c7 ckpt 3650: pre-release: v0.117.2: PinnWire keys are spent only on PinnWire's own word 
  c86d28c7b ckpt 3649: TCL119 PinnWireAccount + ownCountIsGuess (PinnWireTruthTest), TCL120 desk t
  a95cb6071 ckpt 3648: TCL120: desk.json kept ~12,000 cancelled/expired bids (12.5 MB) so the phon
  60ee93c65 ckpt 3647: pre-release: v0.117.2: SX Bet live feed (exchange mid with live scores) as 
  86e96923a ckpt 3646: pre-ship: v0.117.2: SX Bet live feed (exchange mid with live scores) as a l
  803af4e34 ckpt 3645: merged origin/main into branch (docs/tools only on main); v0.117.1 release 
  113525a94 ckpt 3644: pre-release: v0.117.1: refused Novig address replaced at once (no scan on a
  59fa89b6e ckpt 3643: TCL117: SX full mapping doc, real-pair test, TeamMatcher Sam Houston fix
  229e6f635 ckpt 3642: pre-release: v0.117.1: refused Novig address replaced at once and unlimited
```
