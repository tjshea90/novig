# CHECKPOINT 4310 — read me first, then TASKS.md

**Written:** 2026-10-29T02:37:27Z · **tests:** all 9 fast checks green
**Branch:** `ccr-f17ba379-0eynsd` · **builds on:** `5987b75d2` (this checkpoint is the commit after it)

## Just done
KX1/HR1 done: encrypted key export (keys-export.yml), history restore + file import (v1.0.90), keys restore from GitHub (v1.0.91), engine restarted so it holds the new Novig read+trade keys, history file v2 with engine orders (research/history/vigilant-history-2026-10-29.jsonl.gz)

## Do this next
Tj to: create KEYS_EXPORT_PASSPHRASE + run keys-export, import history file. Open ideas: app should restart the engine after a trading-key send even inside the 10 min throttle (sent keys at 22:13 EDT missed the already-started run); in-app import of engine ledger (trades.json) for future gaps; Tracker-tab buttons for the import (layout tests failed when added to the open-bets summary)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  fbecf50b9 ckpt 4309: pre-release: KX1b: bring API keys back from GitHub's encrypted export onto 
  48e940f48 ckpt 4308: pre-release: HR1: restore settled bets and bids from the GitHub backup or a
  7017efa6e ckpt 4307: WATCH 2026-10-28: engine alive, trading key rejected by Novig
  e4376b04e ckpt 4306: ENGINEER 2026-10-28 watch only, engine healthy, no change
  26f9309b3 ckpt 4305: ANALYST 2026-10-28 skipped by rule, engine healthy
  b6d104c9f ckpt 4304: ENGINEER 2026-10-27 watch only, engine healthy, Novig maintenance lock, no 
  125aa3cb8 ckpt 4303: OFF1 done: v1.0.89 released
  910300bd7 ckpt 4302: pre-release: v1.0.89 OFF1: offline copy of engine status, orders, bids and 
  b26d538e4 ckpt 4301: pre-ship: v1.0.89 OFF1: offline copy of engine status, orders, bids and usa
  34ff76bc8 ckpt 4300: OFF1a: offline copy of engine status/orders/bids/usage
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
