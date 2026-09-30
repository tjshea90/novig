# CHECKPOINT 2215 — read me first, then TASKS.md

**Written:** 2026-09-30T21:51:23Z · **tests:** all 3 fast checks green
**Branch:** `ccr-01f0c1f9-xy73ag` · **builds on:** `37f1fbb6` (this checkpoint is the commit after it)

## Just done
pre-release: v0.36.0: Diagnostics fixes from Tj's first report (no false alarms for a busy or backup source, a spent key with another, rare Novig throttles, or imported bets in close coverage; a scan's ParlayAPI cost counted from charged credits, not 600); each scanner's CLV judged on its own; CLV sample sizes; scanner by market; bets keep what made their fair odds; Vigilant's bets against the close listed (versionCode 64, v0.36.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.36.0), then run: bash tools/record-release.sh v0.36.0 64 "v0.36.0: Diagnostics fixes from Tj's first report (no false alarms for a busy or backup source, a spent key with another, rare Novig throttles, or imported bets in close coverage; a scan's ParlayAPI cost counted from charged credits, not 600); each scanner's CLV judged on its own; CLV sample sizes; scanner by market; bets keep what made their fair odds; Vigilant's bets against the close listed"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  bccf7b7e ckpt 2214: W2/W3: Diagnostics false alarms fixed (busy source, backup source, spent key
  b50f4dbc ckpt 2213: Logged Tj's pasted v0.35.0 Diagnostics as TASKS.md W1-W4
  d857f629 ckpt 2212: v0.35.0 (code 63) released and recorded: V1-V4 done
  03124699 ckpt 2211: pre-release: v0.35.0: Bet sheet takes any typed amount and opens at the wall
  7830ace6 ckpt 2210: Fixed a real race in ApiBettingController.placer() (two plans on two threads
  47c2a353 ckpt 2209: V3 done: Diagnostics health checks (FAIL/WARN/OK with evidence and code), ac
  5743ac38 ckpt 2208: V1 done: CNO has no book choice; ParlayAPI has no more sharp books; PropLine
  0b96ecae ckpt 2207: V2 done: Bet sheet Amount field (any amount up to the limit), opens at the w
  e914c36a ckpt 2206: V4 done: live games out of the Check odds now counter; closes dated by their
  1397fbd7 ckpt 2205: V4 audit: CLV correct (pregame close only); CheckOddsStats counts live bets 
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
