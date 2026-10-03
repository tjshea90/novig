# CHECKPOINT 2418 — read me first, then TASKS.md

**Written:** 2026-10-03T03:12:16Z · **tests:** all 3 fast checks green
**Branch:** `ccr-91942f39-sfdkh9` · **builds on:** `dd9b888b` (this checkpoint is the commit after it)

## Just done
BH1-3 data fixes: cancels confirmed (CANCELING), fills read even on 404 / lost answers (searched by clientId), refused cool-off, expiry bounded by the fair's freshness + stop window, fair age required, books agree + sharp veto + game lines need sharp, Kelly sizing (MakerTest 19, mutants 8/8)

## Do this next
BH4-6 app: settings UI (stake mode, max, sharp veto, recommend), approve/deny (MakerDenied store, tab buttons, recommendation notifications with actions), confirm to switch auto-make on, LaunchReset, background banner, auto-bet tab link; fix app tests

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  e8755330 ckpt 2417: BH0 done: v0.51.0 released + recorded (BG6 ticked)
  622feada ckpt 2416: BH: Tj's make-orders follow-up written to TASKS.md (full tests, +EV only, ti
  94cdbdc2 ckpt 2415: pre-release: v0.51.0: make orders (the Bids tab): post-only bids under Vigil
  56e1fac1 ckpt 2414: pre-ship: v0.51.0: make orders (the Bids tab): post-only bids under Vigilant
  e79c9c9b ckpt 2413: BG6e done: Bids tab + MakerRunner + container wiring + Diagnostics + Tracker
  6e894597 ckpt 2412: BG6e in progress: MakerRunner (container: desk, after-scan pass, cancel on o
  9fa8c34e ckpt 2411: BG6a-d done: client ttl/cancel, maker settings, MakerQuote/MakerPlan/MakerLi
  49ed7b56 ckpt 2410: BG1-BG5 ticked; BG6 planned in TASKS.md (6a client, 6b settings, 6c MakerQuo
  6bb1779f ckpt 2409: BG1-BG5 research written: NOVIG_API.md §17 (PO + ttl, cancel, queue, fees, 
  aa59075d ckpt 2408: BG1 API read (docs: PO + ttl, GTT, no amend, queue = price then time, cancel
```

(9 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
