# CHECKPOINT 2894 — read me first, then TASKS.md

**Written:** 2026-10-10T19:06:25Z · **tests:** all 4 fast checks green
**Branch:** `ccr-3fba2f23-lerg26` · **builds on:** `21a237c0` (this checkpoint is the commit after it)

## Just done
pre-release: v0.88.6: pregame make-order bids leave a share of the wallet for the live engines (autopilot sets 50%), so live bids have money to post from (versionCode 179, v0.88.6)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.88.6), then run: bash tools/record-release.sh v0.88.6 179 "v0.88.6: pregame make-order bids leave a share of the wallet for the live engines (autopilot sets 50%), so live bids have money to post from"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  8db65eba ckpt 2893: v0.88.5 released+recorded; website feed ruled out for live (cached); waiting
  4e3604c0 ckpt 2892: pre-release: v0.88.5: Pinnacle website feed measured as cached (90-400 s old
  d636d96a ckpt 2891: pre-release: v0.88.4: feed race counts one sample per update (not per market
  45d02b7c ckpt 2890: pre-release: v0.88.3: Pinnacle compare run says whether the socket and the w
  54922bf6 ckpt 2889: pre-release: v0.88.2: Pinnacle website compare run shows what its own book r
  a7a8121a ckpt 2888: pre-release: v0.88.1: Pinnacle website feed treats a 403 among answers as th
  0b82c1a0 ckpt 2887: TN5 done: trial-replacement research (research file, RESEARCH 128); TN6-TN9 
  258ee08b ckpt 2886: v0.88.0 released+recorded
  e3b3b1d1 ckpt 2885: pre-release: v0.88.0: Pinnacle website feed (free) as a choice beside the Pi
  7aa68f72 ckpt 2884: TN2/TN3 built: website feed wired (feed choice, compare race, UI, diagnostic
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
