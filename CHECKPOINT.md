# CHECKPOINT 3608 — read me first, then TASKS.md

**Written:** 2026-10-17T23:10:10Z · **tests:** all 5 fast checks green
**Branch:** `claude/wizardly-meitner-bgqwhw` · **builds on:** `0c7546a44` (this checkpoint is the commit after it)

## Just done
pre-release: v0.114.34: live fee 0.06 in every league (fallback table), Feed summary row no longer shifts during a scan (versionCode 330, v0.114.34)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.114.34), then run: bash tools/record-release.sh v0.114.34 330 "v0.114.34: live fee 0.06 in every league (fallback table), Feed summary row no longer shifts during a scan"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  0c7546a44 ckpt 3607: pre-ship: v0.114.34: live fee 0.06 in every league (fallback table), Feed s
  6c8fbf940 ckpt 3606: TCL107 audit, fee 0.06 all leagues, feed row jump fix
  0166a8abc ckpt 3605: v0.114.33 released (list jump fix)
  191eb1d56 ckpt 3604: pre-release: v0.114.33: the +EV list no longer jumps: Scanning/No +EV state
  62b89ce5f ckpt 3603: TCL104b list jump fix (fixed-height live banner), TCL106 kalshi research
  29751b60d ckpt 3602: chat session done: v0.114.32 released
  03fe938dd ckpt 3601: pre-release: v0.114.32: the bid desk no longer stops a whole pass when Novi
  ef290d521 ckpt 3600: TCL105 bug-hunt rule written
  b0ee40c7c ckpt 3599: pre-ship: v0.114.32: the bid desk no longer stops a whole pass when Novig l
  7b1e5d9ac ckpt 3598: TCL103: bid desk no longer stops the pass on EVENT_LOCKED
```
