# CHECKPOINT 4209 — read me first, then TASKS.md

**Written:** 2026-10-25T15:12:36Z · **tests:** all 9 fast checks green
**Branch:** `ccr-be05497a-18skm3` · **builds on:** `c353f654d` (this checkpoint is the commit after it)

## Just done
pre-release: v1.0.83 PS: PropLine + ParlayAPI audit: ParlayAPI stream overlay fixed (quotes tagged at parse), ParlayAPI hot streams, PropLine/ParlayAPI cross-check, 13 more PropLine books, PropLine 1st halves, ParlayAPI Pinnacle live feed (paper), real bets graded by PropLine /clv/grade, restart button keep (versionCode 423, v1.0.83)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.83), then run: bash tools/record-release.sh v1.0.83 423 "v1.0.83 PS: PropLine + ParlayAPI audit: ParlayAPI stream overlay fixed (quotes tagged at parse), ParlayAPI hot streams, PropLine/ParlayAPI cross-check, 13 more PropLine books, PropLine 1st halves, ParlayAPI Pinnacle live feed (paper), real bets graded by PropLine /clv/grade, restart button keep"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  c353f654d ckpt 4208: pre-ship: v1.0.83 PS: PropLine + ParlayAPI audit: ParlayAPI stream overlay 
  e02194fe6 ckpt 4207: PB17b ParlayLiveFeed (paper-gated) + PinnFrames shared; tests green; TASKS/
  e0a1e8c59 ckpt 4206: PS1-PS12 written up (research/propline_parlay_audit_2026-10-25.md, CHANGES.
  73f465f45 ckpt 4205: PB23 root cause fixed: ParlayAPI quotes tagged at parse so the stream overl
  5a82f1a63 ckpt 4204: PS9a PropLine 1st-half source propline_1h (PropLinePeriodTest); PB21b cross
  686633040 ckpt 4203: PS1 hot streams + PB21b cross-check + PS4 PropLine re-read 90s; PS5/PS6 ver
  452e77ed0 ckpt 4202: PS2 answered + fixed (13 PropLine books, same-desk groups, research/proplin
  f860b9505 ckpt 4201: PS2 coverage probe (native keys) dispatched; PS3 tools/propline_grade.py wr
  f41f81703 ckpt 4200: PS written into TASKS.md (Tj's stream audit prompt)
  5dc9fa76b ckpt 4199: ANALYST 2026-10-25 skipped under gate, WATCH ok
```
