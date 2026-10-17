# CHECKPOINT 3573 — read me first, then TASKS.md

**Written:** 2026-10-17T18:33:25Z · **tests:** all 5 fast checks green
**Branch:** `claude/keen-keller-8fzr9r` · **builds on:** `83a397b37` (this checkpoint is the commit after it)

## Just done
pre-release: v0.114.24: the Live tab shares its stream budget across all live games instead of three games taking it all (versionCode 320, v0.114.24)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.114.24), then run: bash tools/record-release.sh v0.114.24 320 "v0.114.24: the Live tab shares its stream budget across all live games instead of three games taking it all"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  c384f4beb ckpt 3572: TCL97a: Live tab shares its 48-market budget across all live games
  bc150e315 ckpt 3571: pre-release: v0.114.23: PropLine +EV lists only lines Vigilant agrees on, s
  602eb667e ckpt 3570: TCL96: update-download poll slowdown; PropLine hourly
  18b7ecac8 ckpt 3569: TCL95a: PropLine +EV screen lists only lines where Vigilant agrees (+EV) by
  361060abc ckpt 3568: pre-release: v0.114.22: Live tab and lab find NCAAF/NCAAB games on ESPN (FB
  cee0c9fd5 ckpt 3567: PropLine +EV list carried over a restart (engine) so the card never vanishe
  d9661c733 ckpt 3566: pre-ship: v0.114.22: Live tab and lab find NCAAF/NCAAB games on ESPN (FBS+F
  b554fd8b1 ckpt 3565: TCL94a: live/lab/feed-race ESPN scoreboards read FBS+FCS groups (the plain 
  3dcceb596 ckpt 3564: TCL93a/b: engine sees PropLine refresh mid-scan; advice headers show age
  c193b2270 ckpt 3563: pre-release: v0.114.21: PropLine +EV: the Bet button sits on every card (op
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
