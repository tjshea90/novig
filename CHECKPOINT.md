# CHECKPOINT 3399 — read me first, then TASKS.md

**Written:** 2026-10-16T01:25:29Z · **tests:** all 5 fast checks green
**Branch:** `ccr-6cce5eff-xcxliy` · **builds on:** `6f2fdfddd` (this checkpoint is the commit after it)

## Just done
pre-release: v0.113.0: app updates from GitHub: notification, Install update button, in-app download and install (versionCode 293, v0.113.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.113.0), then run: bash tools/record-release.sh v0.113.0 293 "v0.113.0: app updates from GitHub: notification, Install update button, in-app download and install"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6f2fdfddd ckpt 3398: pre-ship: v0.113.0: app updates from GitHub: notification, Install update b
  4e59400bd ckpt 3397: pre-ship: v0.113.0: app updates from GitHub: notification, Install update b
  8c71b6585 ckpt 3396: TCL31 shipped v0.112.0: engine posted 194 live bids on first pass; released
  8899d4553 ckpt 3395: pre-release: v0.112.0: the GitHub engine posts bids too; the phone syncs ev
  bc2270586 ckpt 3394: pre-ship: v0.112.0: the GitHub engine posts bids too; the phone syncs every
  7336f94f8 ckpt 3393: TCL31f: phone stands down its bid desk when status.deskLive, fetches desk.j
  124582be2 ckpt 3392: TCL31a-e: engine bid desk built (CloudBidRunner, wired in CloudEngine/Cloud
  b7de504cd ckpt 3391: TCL31 planned: engine bid desk (see TASKS.md TCL31)
  fed8df095 ckpt 3390: Dormancy also by slot window (a fresh routine container is acct-unknown); t
  1724f3455 ckpt 3389: Dormancy safeguard: this account's routine stops at rotation checkin / coor
```
