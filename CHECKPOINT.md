# CHECKPOINT 4372 — read me first, then TASKS.md

**Written:** 2026-11-04T08:28:05Z · **tests:** all 9 fast checks green
**Branch:** `claude/optimistic-curie-j7tra9` · **builds on:** `d0064f30d` (this checkpoint is the commit after it)

## Just done
pre-release: Odds API keys used in order (a throttle no longer moves the engine along the list); Volume mode honors Unlimited bids (versionCode 440, v1.0.100)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.100), then run: bash tools/record-release.sh v1.0.100 440 "Odds API keys used in order (a throttle no longer moves the engine along the list); Volume mode honors Unlimited bids"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  43d165dcd ckpt 4371: OK1+BD: tests green
  8093534d8 ckpt 4370: OK1: Odds API sticky keys now return to the first usable key
  c9736992e ckpt 4369: BoltOdds off per Tj
  27bee0475 ckpt 4368: answered socket question; logged SK2
  22f21aca9 ckpt 4367: WATCH 11-03 slot2: engine healthy, read key recovered
  3195b4c54 ckpt 4366: ENGINEER 11-03 slot1: stood down, read-key refusal awaits Tj
  86753d885 ckpt 4365: ANALYST 11-03 slot0: skipped analysis, WATCH found Novig read key refused; 
  16548915c ckpt 4364: ER1: engine restarted at Tj's word 04:32 UTC
  6becc26f4 ckpt 4363: ENGINEER 11-02 slot2: stood down, engine off per ER1
  0a8c8dc05 ckpt 4362: ER1: engine off at Tj's word, restart due 2026-11-03 03:10 UTC
```
