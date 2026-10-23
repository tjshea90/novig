# CHECKPOINT 4042 — read me first, then TASKS.md

**Written:** 2026-10-23T02:25:52Z · **tests:** all 9 fast checks green
**Branch:** `claude/optimistic-curie-lpr7ge` · **builds on:** `86dc43e69` (this checkpoint is the commit after it)

## Just done
pre-release: CH71a an order that finds nobody selling (or a refusal that says the bet is gone) takes the row off the scanned list until a newer price lists it again (versionCode 400, v1.0.60)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.60), then run: bash tools/record-release.sh v1.0.60 400 "CH71a an order that finds nobody selling (or a refusal that says the bet is gone) takes the row off the scanned list until a newer price lists it again"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  86dc43e69 ckpt 4041: bump 1.0.60
  e588f8528 ckpt 4040: CH71a: NotFilled/gone order results take the row off the list
  151a44cf8 ckpt 4039: pre-release: CH70 Kalshi cards and slip match Novig's: X, Bet opens the sam
  96965c89c ckpt 4038: CH70: Kalshi card = Novig card (X, Bet mini slip, Open in Kalshi), hide per
  998597a65 ckpt 4037: pre-release: CH68d Kalshi: when an order is refused for balance, the app mo
  b7de370d5 ckpt 4036: CH68d: cross-shard cash move on insufficient_balance; bump 1.0.58
  6bd1b1c07 ckpt 4035: pre-release: CH68c Kalshi refusal shows the cash on each exchange shard; th
  09b8d77e1 ckpt 4034: CH68c: shard breakdown in refusal; NO-first removed; bump 1.0.57
  3bc4a2ed5 ckpt 4033: pre-release: CH68b Kalshi NO buys use the order form that names the NO side
  165053371 ckpt 4032: CH68b: NO buys try the legacy route first; bump 1.0.56
```
