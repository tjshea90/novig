# CHECKPOINT 2698 — read me first, then TASKS.md

**Written:** 2026-10-07T17:10:44Z · **tests:** all 4 fast checks green
**Branch:** `ccr-b67c8afa-h14i94` · **builds on:** `ab16bcd3` (this checkpoint is the commit after it)

## Just done
pre-release: v0.72.4: small-prop guard on the Auto-bet tab: no one kind of player prop (NHL shots on goal) may take more than 25% of the last 24 hours' auto-bets (never stricter than an even split of the kinds being bet) or more than 3 on one game; all three limits are yours to change or turn off; Diagnostics shows what the last day looked like (RESEARCH 109: shots on goal unproven, concentration the fault) (versionCode 130, v0.72.4)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.72.4), then run: bash tools/record-release.sh v0.72.4 130 "v0.72.4: small-prop guard on the Auto-bet tab: no one kind of player prop (NHL shots on goal) may take more than 25% of the last 24 hours' auto-bets (never stricter than an even split of the kinds being bet) or more than 3 on one game; all three limits are yours to change or turn off; Diagnostics shows what the last day looked like (RESEARCH 109: shots on goal unproven, concentration the fault)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  ab16bcd3 ckpt 2697: pre-release: v0.72.4: small-prop guard (share cap with an even-split floor, 
  8efd407b ckpt 2696: DI3: small-prop guard core: PropGuard (share cap with even-split floor, per-
  f3b9fbb4 ckpt 2695: pre-release: v0.72.3: Low API usage no longer hard-sets the trap guard windo
  2d31323a ckpt 2694: pre-release: v0.72.3: Low API usage no longer hard-sets the trap guard windo
  b9b0315d ckpt 2693: DI1: Low API usage no longer hard-sets the trap guard window (follows trap h
  ffcbecc9 ckpt 2692: Tj's CNO-only scanner filter request written into TASKS.md (DI5)
  d9e9d0d4 ckpt 2691: Tj's new request written into TASKS.md (DI1-DI4): Low API usage hard-set tra
  1293d0d4 ckpt 2690: TASKS cleanup: DA6, DE1/2/5, DG7, DG8 closed with evidence
  dfb2cfd8 ckpt 2689: tape 2 analyzed: RESEARCH 106.2 (Sofascore ahead of Novig by 3 s+ in 20 of 2
  061eef3c ckpt 2688: v0.72.2 released and recorded; DH0/1/3/4/5 done
```
