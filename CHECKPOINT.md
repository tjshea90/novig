# CHECKPOINT 3526 — read me first, then TASKS.md

**Written:** 2026-10-17T05:37:57Z · **tests:** all 5 fast checks green
**Branch:** `ccr-b02146f9-09npa9` · **builds on:** `5bda9c5ec` (this checkpoint is the commit after it)

## Just done
pre-release: v0.114.15: Run now / Stop now for the GitHub engine from Settings > Cloud scan, even outside its hours; the schedule is untouched; study now records Novig's overround per bet (versionCode 311, v0.114.15)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.114.15), then run: bash tools/record-release.sh v0.114.15 311 "v0.114.15: Run now / Stop now for the GitHub engine from Settings > Cloud scan, even outside its hours; the schedule is untouched; study now records Novig's overround per bet"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  5bda9c5ec ckpt 3525: TCL74: manual Run/Stop of the engine outside hours (CloudManual, config fie
  ccd7c6029 ckpt 3524: pre-release: v0.114.14: red STAY AWAY banner on bet types that lose in your
  86d1a353c ckpt 3523: TCL73: profile regenerated (2210 records), engine hard-blocks proven-losing
  65b90b24a ckpt 3522: TCL72 done: totals in taker kinds, engine restarted, Tj told
  6c59b860c ckpt 3521: TCL72: P&L analysis, totals added to taker kinds (class B), research note +
  922ab1cf6 ckpt 3520: TCL72 written
  d98f9f443 ckpt 3519: chat_wake: manual WAKE_TEST dispatch
  2c75d2ea3 ckpt 3518: TCL71a bolt confirmed working on engine; TCL70a/b done except Tj setup
  805e790d6 ckpt 3517: TCL70: chat_wake.py + chat-wake.yml installed on cloud-config, CHAT_WAKE.md
  1ce63dca6 ckpt 3516: TCL71a/b code edits (bolt socket forced on engine, PropLine shares), TASKS 
```
