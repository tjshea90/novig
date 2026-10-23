# CHECKPOINT 4067 — read me first, then TASKS.md

**Written:** 2026-10-23T22:31:53Z · **tests:** all 9 fast checks green
**Branch:** `claude/optimistic-curie-yprd2y` · **builds on:** `74969c12a` (this checkpoint is the commit after it)

## Just done
pre-release: ST1: one combined star rating (type of bet, lead time, small-book cap); history stars removed (versionCode 404, v1.0.64)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.64), then run: bash tools/record-release.sh v1.0.64 404 "ST1: one combined star rating (type of bet, lead time, small-book cap); history stars removed"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  1068f47a7 ckpt 4066: ST1: single star rating (type+lead points, small-book cap), study written, 
  98fa56d7f ckpt 4065: ST1: history folded into the one BetRating (typePoints), history line remov
  97a24d64a ckpt 4064: CH76 done, reported keys are not Growth
  43c0797c9 ckpt 4063: CH76 rank BoltOdds keys by plan
  70d51597a ckpt 4062: CH76 log BoltOdds plan line
  7814b1198 ckpt 4061: CH76 BoltOdds Growth one-socket mode
  6bbea3766 ckpt 4060: research index rebuilt
  fc232f2b3 ckpt 4059: CH75 bid side split answered
  61d6cb984 ckpt 4058: WATCH 2026-10-23: engine healthy, pinnacle+bolt-props feeds silent (watched
  e672aec4a ckpt 4057: pre-release: CH74: Bids tab controls the GitHub engine's bids: Recommend + 
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
