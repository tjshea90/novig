# CHECKPOINT 3168 — read me first, then TASKS.md

**Written:** 2026-10-13T20:28:43Z · **tests:** all 4 fast checks green
**Branch:** `ccr-b57fa2a2-ftkkrm` · **builds on:** `d6da05ba3` (this checkpoint is the commit after it)

## Just done
pre-release: v0.96.3: every API reconnects by itself: a refused key rests 2 min and grows to 1 h instead of all day, a source that was down is asked again after 60 s, every Test button lifts a refusal (versionCode 250, v0.96.3)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.96.3), then run: bash tools/record-release.sh v0.96.3 250 "v0.96.3: every API reconnects by itself: a refused key rests 2 min and grows to 1 h instead of all day, a source that was down is asked again after 60 s, every Test button lifts a refusal"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  d6da05ba3 ckpt 3167: pre-ship: v0.96.3: every API reconnects by itself: a refused key rests 2 mi
  5b9a0ecc8 ckpt 3166: TRC: refused keys retry on a short escalating hold; down() sources re-probe
  1e2be7fa0 ckpt 3165: v0.96.2 released and recorded
  6cf96aad2 ckpt 3164: v0.96.2 shipped to main (floor green 3470): thin-early SOG haircut; v0.96.1
  33aad3071 ckpt 3163: pre-release: v0.96.2: NHL shots-on-goal fair prices built from fewer than 5
  a910bb683 ckpt 3162: pre-ship: v0.96.2: NHL shots-on-goal fair prices built from fewer than 5 bo
  d2ed1b8a4 ckpt 3161: TSOH tasks written
  09a2fa56a ckpt 3160: TSOG: matching audited + ShotsOnGoalMatchTest; research/nhl_sog_matching_20
  91a7e2495 ckpt 3159: TSOG tasks written
  c7a47a248 ckpt 3158: pre-release: v0.96.1: the card shows the stars the auto-bet holds against y
```
