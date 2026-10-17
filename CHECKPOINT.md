# CHECKPOINT 3584 — read me first, then TASKS.md

**Written:** 2026-10-17T19:31:43Z · **tests:** all 5 fast checks green
**Branch:** `claude/keen-keller-8fzr9r` · **builds on:** `7cb98b00e` (this checkpoint is the commit after it)

## Just done
pre-release: v0.114.27: Live light logs every decision (scans with score and clock, offers, bids and why it passed) to GitHub for analysis; the Live tab shares its budget; Unabated turned off by the API review (versionCode 323, v0.114.27)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.114.27), then run: bash tools/record-release.sh v0.114.27 323 "v0.114.27: Live light logs every decision (scans with score and clock, offers, bids and why it passed) to GitHub for analysis; the Live tab shares its budget; Unabated turned off by the API review"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  7cb98b00e ckpt 3583: pre-ship: v0.114.27: Live light logs every decision (scans with score and c
  e11340b99 ckpt 3582: TCL101a: API review; Unabated off; BoltOdds idle flagged to Tj
  da30810d2 ckpt 3581: TCL100a/b: Live light journal (scan/offer/bid) saved to phone-data phone/li
  8055f04e5 ckpt 3580: pre-release: v0.114.26: ParlayAPI and other monthly credits are paced fairl
  33fd7dc72 ckpt 3579: pre-ship: v0.114.26: ParlayAPI and other monthly credits are paced fairly f
  53cc017be ckpt 3578: TCL99a: credit pacing no longer locks out a key first met mid-month with a 
  dbe32328a ckpt 3577: v0.114.25 released: PropLine +EV list retired; engine restarted with the sw
  153616c86 ckpt 3576: pre-release: v0.114.25: the PropLine +EV list is retired (its prop Overs lo
  14425b4cd ckpt 3575: TCL98: PropLine +EV list retired (engine sweep off, app tab and card hidden
  06053c550 ckpt 3574: v0.114.24 released: live tab budget, PropLine changes, account-wide game ca
```
