# CHECKPOINT 3576 — read me first, then TASKS.md

**Written:** 2026-10-17T18:48:36Z · **tests:** all 5 fast checks green
**Branch:** `claude/keen-keller-8fzr9r` · **builds on:** `4e3daaf54` (this checkpoint is the commit after it)

## Just done
pre-release: v0.114.25: the PropLine +EV list is retired (its prop Overs lose; no tab, no card, the engine no longer sweeps it); PropLine odds, CLV and grading stay (versionCode 321, v0.114.25)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.114.25), then run: bash tools/record-release.sh v0.114.25 321 "v0.114.25: the PropLine +EV list is retired (its prop Overs lose; no tab, no card, the engine no longer sweeps it); PropLine odds, CLV and grading stay"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  14425b4cd ckpt 3575: TCL98: PropLine +EV list retired (engine sweep off, app tab and card hidden
  06053c550 ckpt 3574: v0.114.24 released: live tab budget, PropLine changes, account-wide game ca
  076ad483e ckpt 3573: pre-release: v0.114.24: the Live tab shares its stream budget across all li
  c384f4beb ckpt 3572: TCL97a: Live tab shares its 48-market budget across all live games
  bc150e315 ckpt 3571: pre-release: v0.114.23: PropLine +EV lists only lines Vigilant agrees on, s
  602eb667e ckpt 3570: TCL96: update-download poll slowdown; PropLine hourly
  18b7ecac8 ckpt 3569: TCL95a: PropLine +EV screen lists only lines where Vigilant agrees (+EV) by
  361060abc ckpt 3568: pre-release: v0.114.22: Live tab and lab find NCAAF/NCAAB games on ESPN (FB
  cee0c9fd5 ckpt 3567: PropLine +EV list carried over a restart (engine) so the card never vanishe
  d9661c733 ckpt 3566: pre-ship: v0.114.22: Live tab and lab find NCAAF/NCAAB games on ESPN (FBS+F
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
