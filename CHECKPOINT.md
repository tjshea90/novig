# CHECKPOINT 3580 — read me first, then TASKS.md

**Written:** 2026-10-17T19:14:06Z · **tests:** all 5 fast checks green
**Branch:** `claude/keen-keller-8fzr9r` · **builds on:** `33fd7dc72` (this checkpoint is the commit after it)

## Just done
pre-release: v0.114.26: ParlayAPI and other monthly credits are paced fairly for a key first met mid-month (no more false 'out of usage today') (versionCode 322, v0.114.26)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.114.26), then run: bash tools/record-release.sh v0.114.26 322 "v0.114.26: ParlayAPI and other monthly credits are paced fairly for a key first met mid-month (no more false 'out of usage today')"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  33fd7dc72 ckpt 3579: pre-ship: v0.114.26: ParlayAPI and other monthly credits are paced fairly f
  53cc017be ckpt 3578: TCL99a: credit pacing no longer locks out a key first met mid-month with a 
  dbe32328a ckpt 3577: v0.114.25 released: PropLine +EV list retired; engine restarted with the sw
  153616c86 ckpt 3576: pre-release: v0.114.25: the PropLine +EV list is retired (its prop Overs lo
  14425b4cd ckpt 3575: TCL98: PropLine +EV list retired (engine sweep off, app tab and card hidden
  06053c550 ckpt 3574: v0.114.24 released: live tab budget, PropLine changes, account-wide game ca
  076ad483e ckpt 3573: pre-release: v0.114.24: the Live tab shares its stream budget across all li
  c384f4beb ckpt 3572: TCL97a: Live tab shares its 48-market budget across all live games
  bc150e315 ckpt 3571: pre-release: v0.114.23: PropLine +EV lists only lines Vigilant agrees on, s
  602eb667e ckpt 3570: TCL96: update-download poll slowdown; PropLine hourly
```
