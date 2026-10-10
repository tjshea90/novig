# CHECKPOINT 2856 — read me first, then TASKS.md

**Written:** 2026-10-10T08:06:45Z · **tests:** all 4 fast checks green
**Branch:** `ccr-bab5067e-obkelv` · **builds on:** `0f9f6c05` (this checkpoint is the commit after it)

## Just done
pre-release: v0.85.8: the diagnostics file reads each source with its own deadline and leaves out (and names) any that is too slow (versionCode 168, v0.85.8)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.85.8), then run: bash tools/record-release.sh v0.85.8 168 "v0.85.8: the diagnostics file reads each source with its own deadline and leaves out (and names) any that is too slow"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  0f9f6c05 ckpt 2855: pre-ship: v0.85.8: the diagnostics file reads each source with its own deadl
  5f5e2fdc ckpt 2854: pre-ship: v0.85.8: the diagnostics file reads each source with its own deadl
  df3915a5 ckpt 2853: v0.85.7 released and recorded
  951b7eae ckpt 2852: TG1: tail 18-0 is 3 games of one repeated bet under the explore rules
  e52fb5d6 ckpt 2851: Analysed Tj's research file v0.85.5 (research/research_file_2026-10-10/ANALY
  eed059dc ckpt 2850: pre-release: v0.85.7: research share sends only what is new since the last s
  b33113ae ckpt 2849: pre-ship: v0.85.7: research share sends only what is new since the last shar
  6720210f ckpt 2848: v0.85.6 released and recorded
  569b60bd ckpt 2847: scan study analysis: corrected the prop sharp-book claim (kept CLV +0.49 vs 
  6a0447ef ckpt 2846: Analysed Tj's scan study v0.85.3: research/scan_study_2026-10-10/ANALYSIS.md
```
