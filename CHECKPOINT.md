# CHECKPOINT 3648 — read me first, then TASKS.md

**Written:** 2026-10-18T16:21:42Z · **tests:** all 5 fast checks green
**Branch:** `ccr-5a7eb2e9-bn8drh` · **builds on:** `5670325b7` (this checkpoint is the commit after it)

## Just done
TCL120: desk.json kept ~12,000 cancelled/expired bids (12.5 MB) so the phone could not load it and showed 0 resting bids; ended unfilled bids now kept 15 min (test added)

## Do this next
restart the engine so desk.json shrinks, confirm phone shows resting bids; then TCL119 PinnWireAccount (/v1/me) + stop self-counting for PINNWIRE

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  60ee93c65 ckpt 3647: pre-release: v0.117.2: SX Bet live feed (exchange mid with live scores) as 
  86e96923a ckpt 3646: pre-ship: v0.117.2: SX Bet live feed (exchange mid with live scores) as a l
  803af4e34 ckpt 3645: merged origin/main into branch (docs/tools only on main); v0.117.1 release 
  113525a94 ckpt 3644: pre-release: v0.117.1: refused Novig address replaced at once (no scan on a
  59fa89b6e ckpt 3643: TCL117: SX full mapping doc, real-pair test, TeamMatcher Sam Houston fix
  229e6f635 ckpt 3642: pre-release: v0.117.1: refused Novig address replaced at once and unlimited
  965f1de5c ckpt 3641: TCL116: address gate before any API (probe, addressBlocked, test); TCL115 s
  a5dbae061 ckpt 3640: TCL115 tests green; analysis 2026-10-18 written; versionCode 334
  21e791f41 ckpt 3639: TCL114: runner replacement rule + full engine admin documented (ENGINE_ADMI
  3a59130e8 ckpt 3642: reverted AddressWatch MIN_AGE to 4 min (loosening was blocked; awaiting Tj'
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
