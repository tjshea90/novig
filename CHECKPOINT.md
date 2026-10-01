# CHECKPOINT 2269 — read me first, then TASKS.md

**Written:** 2026-10-01T18:22:03Z · **tests:** all 3 fast checks green
**Branch:** `ccr-650fc40f-rtt4er` · **builds on:** `660891ed` (this checkpoint is the commit after it)

## Just done
pre-release: v0.40.0: Add money in every Bet sheet ($1, 2, 5, 10, 15, 20 or typed; sent right from the sheet with a saved management key), a 5 second check for auto-bet, and a pop-up notification for every auto-bet with its stake and EV (plus a test button and a blocked-notification warning) (versionCode 73, v0.40.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.40.0), then run: bash tools/record-release.sh v0.40.0 73 "v0.40.0: Add money in every Bet sheet ($1, 2, 5, 10, 15, 20 or typed; sent right from the sheet with a saved management key), a 5 second check for auto-bet, and a pop-up notification for every auto-bet with its stake and EV (plus a test button and a blocked-notification warning)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  3d12391d ckpt 2268: AH3-AH5 built: 5 s interval (cadence fixed), stake sentences pinned by tests
  b20b948d ckpt 2267: wrote Tj's four-part request (add-money in every bet slip, auto-bet stake ru
  e7b75633 ckpt 2266: v0.39.3 released and recorded: auto-bet one-cent floor (AG1-AG3)
  b2ad578c ckpt 2265: pre-release: v0.39.3: auto-bet has no $1 minimum: stakes down to one cent (K
  20a42607 ckpt 2264: wrote Tj's no-$1-minimum request into TASKS.md as AG1-AG3
  06e8ccbb ckpt 2263: answered Tj's phone-off question; RESEARCH §54 + AF1-AF3 done (no code chan
  feb94e04 ckpt 2262: wrote Tj's phone-off auto-bet question into TASKS.md as AF1-AF3
  f8800188 ckpt 2261: v0.39.2 released and recorded: auto-bet longest-odds limit (AE1-AE3)
  c133deb1 ckpt 2260: pre-release: v0.39.2: auto-bet gets a longest-odds limit (Settings › Betti
  947c80cf ckpt 2259: wrote Tj's longest-odds request into TASKS.md as AE1-AE3 (answer: Kelly scal
```

(10 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
