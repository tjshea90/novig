# CHECKPOINT 2899 — read me first, then TASKS.md

**Written:** 2026-10-10T20:16:46Z · **tests:** all 4 fast checks green
**Branch:** `claude/new-session-mng5sq` · **builds on:** `b16d7aa1` (this checkpoint is the commit after it)

## Just done
pre-release: v0.88.8: live engine shares the app's one Novig stream (a second connection starved on the key's shared stream bucket: 'live book not subscribed yet'), its markets pinned ahead of scans' (versionCode 181, v0.88.8)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.88.8), then run: bash tools/record-release.sh v0.88.8 181 "v0.88.8: live engine shares the app's one Novig stream (a second connection starved on the key's shared stream bucket: 'live book not subscribed yet'), its markets pinned ahead of scans'"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  b16d7aa1 ckpt 2898: pre-ship: v0.88.8: live engine shares the app's one Novig stream (a second c
  56744e2b ckpt 2897: v0.88.7 released+recorded (live book refusal fix + reason words)
  61d6de37 ckpt 2896: pre-release: v0.88.7: live bids: a market Novig refuses no longer shuts the 
  9e16ef7f ckpt 2895: pre-ship: v0.88.7: live bids: a market Novig refuses no longer shuts the liv
  12ce33df ckpt 2894: pre-release: v0.88.6: pregame make-order bids leave a share of the wallet fo
  8db65eba ckpt 2893: v0.88.5 released+recorded; website feed ruled out for live (cached); waiting
  4e3604c0 ckpt 2892: pre-release: v0.88.5: Pinnacle website feed measured as cached (90-400 s old
  d636d96a ckpt 2891: pre-release: v0.88.4: feed race counts one sample per update (not per market
  45d02b7c ckpt 2890: pre-release: v0.88.3: Pinnacle compare run says whether the socket and the w
  54922bf6 ckpt 2889: pre-release: v0.88.2: Pinnacle website compare run shows what its own book r
```
