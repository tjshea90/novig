# CHECKPOINT 3813 — read me first, then TASKS.md

**Written:** 2026-10-20T05:39:52Z · **tests:** all 6 fast checks green
**Branch:** `claude/optimistic-curie-qatu8l` · **builds on:** `5609379b2` (this checkpoint is the commit after it)

## Just done
pre-release: v1.0.13: API status lights fixed (pinnapi refused = red, PinnWire lit), 'X is working again' message, chat draft kept across tabs, Kalshi slips say Kalshi + close at tap + minimum contract, Kalshi notifications (versionCode 353, v1.0.13)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.13), then run: bash tools/record-release.sh v1.0.13 353 "v1.0.13: API status lights fixed (pinnapi refused = red, PinnWire lit), 'X is working again' message, chat draft kept across tabs, Kalshi slips say Kalshi + close at tap + minimum contract, Kalshi notifications"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  5609379b2 ckpt 3812: v1.0.13 prepared
  b99daaaed ckpt 3811: CH32a-e built (chat draft, Kalshi label, slip closes, notifications, min co
  bc00218ac ckpt 3810: AS1-AS3 built+tested (apiHealth by own keys, Recovered message)
  85699563d ckpt 3809: chat watcher prints thread context; CHAT.md rule
  616f9aec7 ckpt 3808: v1.0.12 released and recorded (Kalshi KV1-KV10 + engine lane)
  e5765cfe7 ckpt 3807: pre-release: v1.0.12: Kalshi beside Novig: a Kalshi tab with its own +EV li
  cd79c234c ckpt 3806: pre-ship: v1.0.12: Kalshi beside Novig: a Kalshi tab with its own +EV list,
  43f18fd32 ckpt 3805: KV9 phone side: GitHub engine card, kalshi.json push, status poll; lane hon
  ea4ae6d69 ckpt 3804: KV9 engine lane: CloudKalshiLane + models + CloudMain wiring (additive) + t
  4764eb008 ckpt 3797: AS tasks written: API status lights lie (pinnacle feed id shared by PinnWir
```
