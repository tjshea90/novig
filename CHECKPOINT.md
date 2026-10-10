# CHECKPOINT 2850 — read me first, then TASKS.md

**Written:** 2026-10-10T07:46:37Z · **tests:** all 4 fast checks green
**Branch:** `ccr-bab5067e-obkelv` · **builds on:** `b33113ae` (this checkpoint is the commit after it)

## Just done
pre-release: v0.85.7: research share sends only what is new since the last share (under 30 MB), with a Share ALL button (versionCode 167, v0.85.7)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.85.7), then run: bash tools/record-release.sh v0.85.7 167 "v0.85.7: research share sends only what is new since the last share (under 30 MB), with a Share ALL button"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  b33113ae ckpt 2849: pre-ship: v0.85.7: research share sends only what is new since the last shar
  6720210f ckpt 2848: v0.85.6 released and recorded
  569b60bd ckpt 2847: scan study analysis: corrected the prop sharp-book claim (kept CLV +0.49 vs 
  6a0447ef ckpt 2846: Analysed Tj's scan study v0.85.3: research/scan_study_2026-10-10/ANALYSIS.md
  d12bc1de ckpt 2845: pre-release: v0.85.6: live bids say why a line has no Pinnacle price (altern
  0f4e884b ckpt 2844: pre-ship: v0.85.6: live bids say why a line has no Pinnacle price (alternate
  f6cb07f8 ckpt 2843: v0.85.5 released and recorded
  1b1245d5 ckpt 2842: pre-release: v0.85.5: diagnostics file reads the last 3 days of each recorde
  51ceaeea ckpt 2841: pre-ship: v0.85.5: diagnostics file reads the last 3 days of each recorder, 
  fe1140c8 ckpt 2840: pre-release: v0.85.4: the live bid desk now actually starts (it never did in
```
