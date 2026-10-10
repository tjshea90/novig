# CHECKPOINT 2896 — read me first, then TASKS.md

**Written:** 2026-10-10T19:47:35Z · **tests:** all 4 fast checks green
**Branch:** `claude/new-session-mng5sq` · **builds on:** `9e16ef7f` (this checkpoint is the commit after it)

## Just done
pre-release: v0.88.7: live bids: a market Novig refuses no longer shuts the live book connection out (batch halved down to it), and 'no current Novig book' now says why (versionCode 180, v0.88.7)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.88.7), then run: bash tools/record-release.sh v0.88.7 180 "v0.88.7: live bids: a market Novig refuses no longer shuts the live book connection out (batch halved down to it), and 'no current Novig book' now says why"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  9e16ef7f ckpt 2895: pre-ship: v0.88.7: live bids: a market Novig refuses no longer shuts the liv
  12ce33df ckpt 2894: pre-release: v0.88.6: pregame make-order bids leave a share of the wallet fo
  8db65eba ckpt 2893: v0.88.5 released+recorded; website feed ruled out for live (cached); waiting
  4e3604c0 ckpt 2892: pre-release: v0.88.5: Pinnacle website feed measured as cached (90-400 s old
  d636d96a ckpt 2891: pre-release: v0.88.4: feed race counts one sample per update (not per market
  45d02b7c ckpt 2890: pre-release: v0.88.3: Pinnacle compare run says whether the socket and the w
  54922bf6 ckpt 2889: pre-release: v0.88.2: Pinnacle website compare run shows what its own book r
  a7a8121a ckpt 2888: pre-release: v0.88.1: Pinnacle website feed treats a 403 among answers as th
  0b82c1a0 ckpt 2887: TN5 done: trial-replacement research (research file, RESEARCH 128); TN6-TN9 
  258ee08b ckpt 2886: v0.88.0 released+recorded
```
