# CHECKPOINT 3616 — read me first, then TASKS.md

**Written:** 2026-10-18T00:08:35Z · **tests:** all 5 fast checks green
**Branch:** `claude/optimistic-curie-ym9o0a` · **builds on:** `6647b4604` (this checkpoint is the commit after it)

## Just done
pre-release: v0.115.0: live betting on GitHub: tail bets, Live light bids, live taker and live bids run on the engine (BoltOdds price, no Pinnodds); phone stands down (versionCode 331, v0.115.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.115.0), then run: bash tools/record-release.sh v0.115.0 331 "v0.115.0: live betting on GitHub: tail bets, Live light bids, live taker and live bids run on the engine (BoltOdds price, no Pinnodds); phone stands down"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6647b4604 ckpt 3615: pre-ship: v0.115.0: live betting on GitHub: tail bets, Live light bids, liv
  870082cf6 ckpt 3614: TCL109 phase 2 built (live taker+bids on engine), phone stand-down, tests
  c15c46783 ckpt 3613: TCL109 phase 1: CloudLiveLane (Live light + tail taker + light bids on the 
  845c20fbc ckpt 3612: TCL109 written
  af935c0c8 ckpt 3611: TCL108 written
  7291762e7 ckpt 3610: ANALYST 2026-10-17: watch ok, no rule change, STATE.json baseline
  d5e6bcda6 ckpt 3609: v0.114.34 released
  d1bbdf4b5 ckpt 3608: pre-release: v0.114.34: live fee 0.06 in every league (fallback table), Fee
  0c7546a44 ckpt 3607: pre-ship: v0.114.34: live fee 0.06 in every league (fallback table), Feed s
  6c8fbf940 ckpt 3606: TCL107 audit, fee 0.06 all leagues, feed row jump fix
```
