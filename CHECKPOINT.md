# CHECKPOINT 2695 — read me first, then TASKS.md

**Written:** 2026-10-07T16:38:00Z · **tests:** all 4 fast checks green
**Branch:** `ccr-b67c8afa-h14i94` · **builds on:** `2d31323a` (this checkpoint is the commit after it)

## Just done
pre-release: v0.72.3: Low API usage no longer hard-sets the trap guard window, the small-market fill, the longest odds or the price band: what you pick wins (the window follows your trap guard hours, Off reads as far as Settings › Scanning says); a longest or shortest odds beyond the price band widens it (versionCode 129, v0.72.3)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.72.3), then run: bash tools/record-release.sh v0.72.3 129 "v0.72.3: Low API usage no longer hard-sets the trap guard window, the small-market fill, the longest odds or the price band: what you pick wins (the window follows your trap guard hours, Off reads as far as Settings › Scanning says); a longest or shortest odds beyond the price band widens it"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  2d31323a ckpt 2694: pre-release: v0.72.3: Low API usage no longer hard-sets the trap guard windo
  b9b0315d ckpt 2693: DI1: Low API usage no longer hard-sets the trap guard window (follows trap h
  ffcbecc9 ckpt 2692: Tj's CNO-only scanner filter request written into TASKS.md (DI5)
  d9e9d0d4 ckpt 2691: Tj's new request written into TASKS.md (DI1-DI4): Low API usage hard-set tra
  1293d0d4 ckpt 2690: TASKS cleanup: DA6, DE1/2/5, DG7, DG8 closed with evidence
  dfb2cfd8 ckpt 2689: tape 2 analyzed: RESEARCH 106.2 (Sofascore ahead of Novig by 3 s+ in 20 of 2
  061eef3c ckpt 2688: v0.72.2 released and recorded; DH0/1/3/4/5 done
  c7990345 ckpt 2687: pre-release: v0.72.2: a preset now carries the auto-bet's favourite bar; pre
  4db49a3e ckpt 2686: DH4 + DH5 done: RESEARCH 107, preset favourite bar, plus-money and favourite
  7e0563fd ckpt 2685: pre-release: v0.72.1: live feed test (opt-in, reads only, no orders) for sco
```
