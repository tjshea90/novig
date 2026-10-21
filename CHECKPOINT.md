# CHECKPOINT 3862 — read me first, then TASKS.md

**Written:** 2026-10-21T00:07:41Z · **tests:** all 6 fast checks green
**Branch:** `claude/optimistic-curie-o9ngjz` · **builds on:** `cf107af52` (this checkpoint is the commit after it)

## Just done
pre-release: v1.0.25: ParlayAPI live play-by-play stream as the engine's score source, Pro key first (CH47) (versionCode 365, v1.0.25)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.25), then run: bash tools/record-release.sh v1.0.25 365 "v1.0.25: ParlayAPI live play-by-play stream as the engine's score source, Pro key first (CH47)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  2dc31786a ckpt 3861: pre-ship: v1.0.25: ParlayAPI live play-by-play stream as the engine's score
  87e8fdbf2 ckpt 3860: CH47: Parlay pool uses biggest paid plan first (test), meter loaded before 
  91bb11618 ckpt 3859: CH47 probe read: PBP SSE real shape, arbs real shape, Pro key found
  36558cfbc ckpt 3858: CH47: tools/parlay.py + parlay.yml (plans, probe)
  d958767df ckpt 3857: ANALYST 2026-10-20: no rule change
  379bfc7c1 ckpt 3856: CH46 closed by Tj
  d9dc79de5 ckpt 3855: CH46b measured: one provider only, asked Tj
  145dde442 ckpt 3854: v1.0.24 released (CH46c), CDN rejected by measurement
  93fcf844d ckpt 3853: pre-release: v1.0.24: ESPN win probability cross-checks live moneyline call
  32feed09a ckpt 3852: pre-ship: v1.0.24: ESPN win probability cross-checks live moneyline calls (
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
