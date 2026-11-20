# CHECKPOINT 4792 — read me first, then TASKS.md

**Written:** 2026-11-20T18:51:37Z · **tests:** all 11 fast checks green
**Branch:** `claude/parlay-profitability-research-fy3tnz` · **builds on:** `69a013492` (this checkpoint is the commit after it)

## Just done
pre-release: PF1: the parlay maker runs on Claude's profit profile; the Parlays tab says so and hides the controls the engine no longer reads (versionCode 486, v1.0.146)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.146), then run: bash tools/record-release.sh v1.0.146 486 "PF1: the parlay maker runs on Claude's profit profile; the Parlays tab says so and hides the controls the engine no longer reads"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  69a013492 ckpt 4791: PF1: CLAUDE.md standing rule for the profile file; waiting for CI on d92a8f
  d92a8f21d ckpt 4790: PF1: profile written to cloud-config rfq-maker-engine.json (81ac24d85) and 
  cd41e97d7 ckpt 4789: PF1: engine profit profile file + precedence over the phone's rfq-maker.jso
  124c900f8 ckpt 4788: MK57: maxLegFair .8 -> .7 (Tj: sucker bets, not sharp heavy-favourite actio
  8ddbf3625 ckpt 4787: MK56: identical-parlay guard (maxSameParlay 1, maxParlaysPerOutcome 2) code
  bba77e4ab ckpt 4786: MK55: 16:20 check: job handover ok (44767621550), 8 fills today (15:37 FSU 
  1486dec0b ckpt 4785: MK54: walletLegShare 0.25 (same two legs filled twice in 7 minutes; per-leg
  e88e2d1a5 ckpt 4784: MK53: MK16 coverage follow-up answered from this session's work (no new bui
  48446df58 ckpt 4783: MK52: MK14 follow-up 2 report: market outcomes release stale (0 graded), ex
  d8818a462 ckpt 4782: MK51: 14:00 check: 6 fills today (13:56 .503/.501 $9.52), 1,278 quotes, cas
```
