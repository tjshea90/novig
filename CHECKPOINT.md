# CHECKPOINT 3826 — read me first, then TASKS.md

**Written:** 2026-10-20T15:00:18Z · **tests:** all 6 fast checks green
**Branch:** `claude/optimistic-curie-yis368` · **builds on:** `4ae216182` (this checkpoint is the commit after it)

## Just done
pre-release: v1.0.17: Engine tab Money view shows Kalshi stats (bets, bids, wallet) apart from Novig, and says why Kalshi bids are not resting (versionCode 357, v1.0.17)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.17), then run: bash tools/record-release.sh v1.0.17 357 "v1.0.17: Engine tab Money view shows Kalshi stats (bets, bids, wallet) apart from Novig, and says why Kalshi bids are not resting"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  4ae216182 ckpt 3825: pre-ship: v1.0.17: Engine tab Money view shows Kalshi stats (bets, bids, wa
  46249149c ckpt 3824: pre-release: v1.0.16: Kalshi cards bet on Kalshi's own slip (no Novig read)
  b8a8de8d3 ckpt 3823: pre-ship: v1.0.16: Kalshi cards bet on Kalshi's own slip (no Novig read), K
  4dd968728 ckpt 3822: ENGINEER 2026-10-20 rotation: research card posted, no change
  f3309473c ckpt 3821: v1.0.15 released (CH33-36); chat quiet since ~07:00
  7c0674b65 ckpt 3820: pre-release: v1.0.15: Research page in the Claude tab (daily analysis summa
  b8170c54d ckpt 3819: pre-ship: v1.0.15: Research page in the Claude tab (daily analysis summary)
  34e3a59f8 ckpt 3818: pre-release: v1.0.14: one +EV tab with Novig/Kalshi toggles and labelled ro
  1850b61ca ckpt 3817: pre-ship: v1.0.14: one +EV tab with Novig/Kalshi toggles and labelled rows 
  dea7514f7 ckpt 3816: CH33 merged +EV tab, CH34 bolt phone-socket guard, CH35 sheet height
```
