# CHECKPOINT 3820 — read me first, then TASKS.md

**Written:** 2026-10-20T07:02:41Z · **tests:** all 6 fast checks green
**Branch:** `claude/optimistic-curie-rlkku2` · **builds on:** `b8170c54d` (this checkpoint is the commit after it)

## Just done
pre-release: v1.0.15: Research page in the Claude tab (daily analysis summary) (versionCode 355, v1.0.15)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.15), then run: bash tools/record-release.sh v1.0.15 355 "v1.0.15: Research page in the Claude tab (daily analysis summary)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  b8170c54d ckpt 3819: pre-ship: v1.0.15: Research page in the Claude tab (daily analysis summary)
  34e3a59f8 ckpt 3818: pre-release: v1.0.14: one +EV tab with Novig/Kalshi toggles and labelled ro
  1850b61ca ckpt 3817: pre-ship: v1.0.14: one +EV tab with Novig/Kalshi toggles and labelled rows 
  dea7514f7 ckpt 3816: CH33 merged +EV tab, CH34 bolt phone-socket guard, CH35 sheet height
  849ed8270 ckpt 3815: CH33 built; CH34/35 written
  117209b18 ckpt 3814: CH33 written (merge Kalshi into +EV tab)
  685bb10e5 ckpt 3813: pre-release: v1.0.13: API status lights fixed (pinnapi refused = red, PinnW
  5609379b2 ckpt 3812: v1.0.13 prepared
  b99daaaed ckpt 3811: CH32a-e built (chat draft, Kalshi label, slip closes, notifications, min co
  bc00218ac ckpt 3810: AS1-AS3 built+tested (apiHealth by own keys, Recovered message)
```
