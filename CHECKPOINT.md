# CHECKPOINT 4497 — read me first, then TASKS.md

**Written:** 2026-11-16T04:28:18Z · **tests:** all 10 fast checks green
**Branch:** `claude/optimistic-curie-99twme` · **builds on:** `65f3bdbf2` (this checkpoint is the commit after it)

## Just done
pre-release: DG5: engine keeps the wallet's whole Novig ledger on GitHub; the phone's money figures read it (versionCode 455, v1.0.115)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.115), then run: bash tools/record-release.sh v1.0.115 455 "DG5: engine keeps the wallet's whole Novig ledger on GitHub; the phone's money figures read it"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  65f3bdbf2 ckpt 4496: bump 1.0.115
  3fe0b810a ckpt 4495: research index rebuilt
  6f8a95c3e ckpt 4494: DG5a: engine publishes wallet-ledger.json, phone reads it for wallet money;
  2cedc9dc1 ckpt 4493: chat: answered 'is it fine/done' with DG5 status
  bbb9a50cf ckpt 4492: DG5 logged + answered Tj; found ledger readers NovigTradingClient.bettingRo
  e51b1ce06 ckpt 4491: chat: DG4 research written, Tj asked go
  cc527242b ckpt 4490: chat: DG4 answered, logged
  32e0ca377 ckpt 4489: chat: v1.0.114 shipped (DG3)
  51f3f5ebc ckpt 4488: pre-release: DG3: phone drops settled bets older than 35 days once GitHub h
  519ef05c0 ckpt 4487: pre-ship: DG3: phone drops settled bets older than 35 days once GitHub hold
```
