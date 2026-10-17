# CHECKPOINT 3529 — read me first, then TASKS.md

**Written:** 2026-10-17T06:38:38Z · **tests:** all 5 fast checks green
**Branch:** `ccr-b02146f9-09npa9` · **builds on:** `3427af01f` (this checkpoint is the commit after it)

## Just done
pre-release: v0.114.16: Engine tab Address tile shows the real Novig verdict (red when refused); GitHub watchdog swaps a refused runner every 5 min; PropLine +EV bets are graded and reported at 300 bets (versionCode 312, v0.114.16)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.114.16), then run: bash tools/record-release.sh v0.114.16 312 "v0.114.16: Engine tab Address tile shows the real Novig verdict (red when refused); GitHub watchdog swaps a refused runner every 5 min; PropLine +EV bets are graded and reported at 300 bets"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  901b20b97 ckpt 3528: v0.114.16: Address tile reads the real verdict, novigAddress status, Addres
  eb1c8c25c ckpt 3527: TCL78: standing job rule at top of CLAUDE.md + ROUTINE.md; address tile fix
  8d7e224a6 ckpt 3526: pre-release: v0.114.15: Run now / Stop now for the GitHub engine from Setti
  5bda9c5ec ckpt 3525: TCL74: manual Run/Stop of the engine outside hours (CloudManual, config fie
  ccd7c6029 ckpt 3524: pre-release: v0.114.14: red STAY AWAY banner on bet types that lose in your
  86d1a353c ckpt 3523: TCL73: profile regenerated (2210 records), engine hard-blocks proven-losing
  65b90b24a ckpt 3522: TCL72 done: totals in taker kinds, engine restarted, Tj told
  6c59b860c ckpt 3521: TCL72: P&L analysis, totals added to taker kinds (class B), research note +
  922ab1cf6 ckpt 3520: TCL72 written
  d98f9f443 ckpt 3519: chat_wake: manual WAKE_TEST dispatch
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
