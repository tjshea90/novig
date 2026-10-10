# CHECKPOINT 2845 — read me first, then TASKS.md

**Written:** 2026-10-10T07:30:14Z · **tests:** all 4 fast checks green
**Branch:** `ccr-bab5067e-obkelv` · **builds on:** `094bc2f2` (this checkpoint is the commit after it)

## Just done
pre-release: v0.85.6: live bids say why a line has no Pinnacle price (alternate strike, no main line, three-way) (versionCode 166, v0.85.6)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.85.6), then run: bash tools/record-release.sh v0.85.6 166 "v0.85.6: live bids say why a line has no Pinnacle price (alternate strike, no main line, three-way)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  0f4e884b ckpt 2844: pre-ship: v0.85.6: live bids say why a line has no Pinnacle price (alternate
  f6cb07f8 ckpt 2843: v0.85.5 released and recorded
  1b1245d5 ckpt 2842: pre-release: v0.85.5: diagnostics file reads the last 3 days of each recorde
  51ceaeea ckpt 2841: pre-ship: v0.85.5: diagnostics file reads the last 3 days of each recorder, 
  fe1140c8 ckpt 2840: pre-release: v0.85.4: the live bid desk now actually starts (it never did in
  b0584777 ckpt 2839: pre-ship: v0.85.4: the live bid desk now actually starts (it never did in v0
  3bc8ffa2 ckpt 2838: v0.85.3 released and recorded
  f132d60f ckpt 2837: pre-release: v0.85.3: live bid desk loop heartbeat and a Details block on th
  6245fed7 ckpt 2836: pre-ship: v0.85.3: live bid desk loop heartbeat and a Details block on the L
  6a173fb1 ckpt 2835: v0.85.2 released and recorded
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
