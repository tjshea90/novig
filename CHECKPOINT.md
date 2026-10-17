# CHECKPOINT 3568 — read me first, then TASKS.md

**Written:** 2026-10-17T18:10:54Z · **tests:** all 5 fast checks green
**Branch:** `claude/keen-keller-8fzr9r` · **builds on:** `50f922d28` (this checkpoint is the commit after it)

## Just done
pre-release: v0.114.22: Live tab and lab find NCAAF/NCAAB games on ESPN (FBS+FCS groups); advice and API-manager headers say how old they are; the engine keeps PropLine's list across restarts (versionCode 318, v0.114.22)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.114.22), then run: bash tools/record-release.sh v0.114.22 318 "v0.114.22: Live tab and lab find NCAAF/NCAAB games on ESPN (FBS+FCS groups); advice and API-manager headers say how old they are; the engine keeps PropLine's list across restarts"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  cee0c9fd5 ckpt 3567: PropLine +EV list carried over a restart (engine) so the card never vanishe
  d9661c733 ckpt 3566: pre-ship: v0.114.22: Live tab and lab find NCAAF/NCAAB games on ESPN (FBS+F
  b554fd8b1 ckpt 3565: TCL94a: live/lab/feed-race ESPN scoreboards read FBS+FCS groups (the plain 
  3dcceb596 ckpt 3564: TCL93a/b: engine sees PropLine refresh mid-scan; advice headers show age
  c193b2270 ckpt 3563: pre-release: v0.114.21: PropLine +EV: the Bet button sits on every card (op
  6af8bdb88 ckpt 3562: pre-ship: v0.114.21: PropLine +EV: the Bet button sits on every card (opens
  cbf33e26d ckpt 3561: v0.114.20 released and recorded; engine restarted with TCL89 engine side an
  e66cc9cf1 ckpt 3560: TCL91a: account-wide per-game exposure for the engine's game cap and same-t
  93fea8fc7 ckpt 3559: pre-release: v0.114.20: PropLine +EV: pull down runs a PropLine-only sweep 
  3b35ef708 ckpt 3558: pre-release: v0.114.20: pull down on the PropLine +EV screen runs a PropLin
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
