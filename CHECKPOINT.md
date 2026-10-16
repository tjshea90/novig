# CHECKPOINT 3479 — read me first, then TASKS.md

**Written:** 2026-10-16T20:02:25Z · **tests:** all 5 fast checks green
**Branch:** `ccr-95eefe49-t2qp21` · **builds on:** `193306028` (this checkpoint is the commit after it)

## Just done
pre-release: v0.114.10: scarce APIs (The Odds API, OddsPapi Free) never lead and fill a gap at most every 30 min; PropLine retries timeouts and 504s and reads big prop lists in chunks; PropLine +EV odds limit (versionCode 306, v0.114.10)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.114.10), then run: bash tools/record-release.sh v0.114.10 306 "v0.114.10: scarce APIs (The Odds API, OddsPapi Free) never lead and fill a gap at most every 30 min; PropLine retries timeouts and 504s and reads big prop lists in chunks; PropLine +EV odds limit"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  193306028 ckpt 3478: propline_clv.py validated on real archive: spreads grouped by size of numbe
  358f74a05 ckpt 3477: pre-ship: v0.114.10: scarce APIs (The Odds API, OddsPapi Free) never lead a
  8d458dd85 ckpt 3476: TCL58: scarce APIs never lead the ranking and a gap is asked at most every 
  90e009e84 ckpt 3475: v0.114.9 released + recorded; engine restarted on PropLine code
  0b1079bd5 ckpt 3474: pre-release: v0.114.9: PropLine Pro: paid key found and used first, every g
  f31e0c6f0 ckpt 3473: pre-ship: v0.114.9: PropLine Pro: paid key found and used first, every game
  8f7a36e9d ckpt 3472: TCL57: SGO NFL guide checked: Vigilant already follows it, documented
  050acec3b ckpt 3471: CLAUDE.md: PropLine Pro rule
  6e2aba5de ckpt 3470: propline probe: finds a finished event from the export; adds player trends/
  7ecf772bf ckpt 3469: propline.yml: clv task + after the daily archive
```
