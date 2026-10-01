# CHECKPOINT 2273 — read me first, then TASKS.md

**Written:** 2026-10-01T18:51:34Z · **tests:** all 3 fast checks green
**Branch:** `ccr-650fc40f-rtt4er` · **builds on:** `6c4139cb` (this checkpoint is the commit after it)

## Just done
pre-release: v0.40.1: Check odds now looks for every closing line (every started bet without one, not the 3-hourly few) and holds a focus while it runs: the CNO scanner, background auto-scan (auto-bet with it), scans and the widget's refresh wait until it ends (versionCode 74, v0.40.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.40.1), then run: bash tools/record-release.sh v0.40.1 74 "v0.40.1: Check odds now looks for every closing line (every started bet without one, not the 3-hourly few) and holds a focus while it runs: the CNO scanner, background auto-scan (auto-bet with it), scans and the widget's refresh wait until it ends"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  f45b3717 ckpt 2272: AI2-AI3 built: forced close backfill (CloseBackfill.run(force)), FocusGate h
  727b540c ckpt 2271: wrote Tj's Check-odds-now request into TASKS.md as AI1-AI4
  a23d901f ckpt 2270: v0.40.0 released and recorded: add money in every Bet sheet, 5 s interval, a
  039249ed ckpt 2269: pre-release: v0.40.0: Add money in every Bet sheet ($1, 2, 5, 10, 15, 20 or 
  3d12391d ckpt 2268: AH3-AH5 built: 5 s interval (cadence fixed), stake sentences pinned by tests
  b20b948d ckpt 2267: wrote Tj's four-part request (add-money in every bet slip, auto-bet stake ru
  e7b75633 ckpt 2266: v0.39.3 released and recorded: auto-bet one-cent floor (AG1-AG3)
  b2ad578c ckpt 2265: pre-release: v0.39.3: auto-bet has no $1 minimum: stakes down to one cent (K
  20a42607 ckpt 2264: wrote Tj's no-$1-minimum request into TASKS.md as AG1-AG3
  06e8ccbb ckpt 2263: answered Tj's phone-off question; RESEARCH §54 + AF1-AF3 done (no code chan
```

(7 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
