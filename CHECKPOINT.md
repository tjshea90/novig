# CHECKPOINT 3172 — read me first, then TASKS.md

**Written:** 2026-10-13T21:29:05Z · **tests:** all 4 fast checks green
**Branch:** `ccr-b57fa2a2-ftkkrm` · **builds on:** `9c5d7c7d0` (this checkpoint is the commit after it)

## Just done
pre-release: v0.96.4: fixes the laggy, glitching feed: the adjusted-stars note on a card no longer squeezes the league/time text into one letter per line (tall empty cards) (versionCode 251, v0.96.4)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.96.4), then run: bash tools/record-release.sh v0.96.4 251 "v0.96.4: fixes the laggy, glitching feed: the adjusted-stars note on a card no longer squeezes the league/time text into one letter per line (tall empty cards)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  9c5d7c7d0 ckpt 3171: pre-ship: v0.96.4: fixes the laggy, glitching feed: the adjusted-stars note
  f2f6bf7d8 ckpt 3170: handoff saved to .handoff/
  7e9ad40f5 ckpt 3169: v0.96.3 released and recorded
  65778d897 ckpt 3168: pre-release: v0.96.3: every API reconnects by itself: a refused key rests 2
  d6da05ba3 ckpt 3167: pre-ship: v0.96.3: every API reconnects by itself: a refused key rests 2 mi
  5b9a0ecc8 ckpt 3166: TRC: refused keys retry on a short escalating hold; down() sources re-probe
  1e2be7fa0 ckpt 3165: v0.96.2 released and recorded
  6cf96aad2 ckpt 3164: v0.96.2 shipped to main (floor green 3470): thin-early SOG haircut; v0.96.1
  33aad3071 ckpt 3163: pre-release: v0.96.2: NHL shots-on-goal fair prices built from fewer than 5
  a910bb683 ckpt 3162: pre-ship: v0.96.2: NHL shots-on-goal fair prices built from fewer than 5 bo
```
