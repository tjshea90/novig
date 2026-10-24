# CHECKPOINT 4136 — read me first, then TASKS.md

**Written:** 2026-10-24T18:17:24Z · **tests:** all 9 fast checks green
**Branch:** `claude/optimistic-curie-hj14wp` · **builds on:** `148cdc6ad` (this checkpoint is the commit after it)

## Just done
pre-release: CH80: the +EV list no longer empties at each engine scan start: rows the new scan has not re-read yet stay listed with their own stamps (found from the phone's FEEDHIDE log) (versionCode 417, v1.0.77)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.77), then run: bash tools/record-release.sh v1.0.77 417 "CH80: the +EV list no longer empties at each engine scan start: rows the new scan has not re-read yet stay listed with their own stamps (found from the phone's FEEDHIDE log)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  148cdc6ad ckpt 4135: pre-ship: CH80: the +EV list no longer empties at each engine scan start: r
  89aa892f4 ckpt 4134: pre-release: After the Diagnostics report is saved to GitHub the phone clea
  f42da88fd ckpt 4133: pre-ship: After the Diagnostics report is saved to GitHub the phone clears 
  04c7f1a26 ckpt 4132: pre-release: The phone saves its full Diagnostics report to GitHub every 20
  45ac45990 ckpt 4131: pre-ship: The phone saves its full Diagnostics report to GitHub every 20 mi
  c27122c3f ckpt 4130: pre-release: CH82+CH81: engine restart reads 'stopped or restarting' not 's
  8589e8341 ckpt 4129: pre-ship: CH82+CH81: engine restart reads 'stopped or restarting' not 'swit
  88138eab6 ckpt 4128: CH80b candidate cause noted (requoteListed partial books)
  70fcf7828 ckpt 4127: CH83 scoped: archive not write-behind
  92ec17a42 ckpt 4126: CH83 written; diagnostics paste triaged
```
