# CHECKPOINT 3888 — read me first, then TASKS.md

**Written:** 2026-10-21T05:09:57Z · **tests:** all 6 fast checks green
**Branch:** `claude/optimistic-curie-7dry2r` · **builds on:** `59be3d1ef` (this checkpoint is the commit after it)

## Just done
pre-release: v1.0.32: picked-off guard counts games not fills (versionCode 372, v1.0.32)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.32), then run: bash tools/record-release.sh v1.0.32 372 "v1.0.32: picked-off guard counts games not fills"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  b25f273fe ckpt 3887: CH54: picked-off guard counts games
  31688d055 ckpt 3886: pre-release: v1.0.31: bids always target the most-the-bids-may-be-worth set
  934647eaf ckpt 3885: fix MakerTest for deployPct 1.0
  28ce4fa12 ckpt 3884: CH53: bid wallet target = the max the user sets
  81914c18b ckpt 3883: pre-release: v1.0.30: refresh arrow flashes, status bar names what is loadi
  b610dc97e ckpt 3882: CH52: pull arrow flashes 1s, status bar shows read in flight
  db4acd910 ckpt 3881: pre-release: v1.0.29: pull-to-refresh deadlines, Stopping headline when swi
  f076e3bff ckpt 3880: CH51: dashboard shows Stopping when switch off; refreshNow reads have 20s d
  ae9575801 ckpt 3875: v1.0.27 released; chat quiet 30 min
  6c12a33be ckpt 3879: v1.0.28 released and recorded; told Tj
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
