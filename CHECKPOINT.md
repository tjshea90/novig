# CHECKPOINT 2425 — read me first, then TASKS.md

**Written:** 2026-10-03T05:02:35Z · **tests:** all 3 fast checks green
**Branch:** `ccr-9491e046-7f6pnb` · **builds on:** `5bfee019` (this checkpoint is the commit after it)

## Just done
BI5 core: maker passes judge running scans (partial: unjudged bids stay), lines use the last scan's Novig book (<=20 min), passes every 20 s during scans, waiting reasons (most bids/dollars/wallet), wallet before/after logged; MakerTest 25 (mutants 3/3)

## Do this next
BI5 app side: MakerAppTest for a partial pass; Bids tab shows waiting reasons + 3-way mode (Off/Recommend/Fully automatic); then BI4/BI8 (tab always shown, turn on what bids need)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  784bead3 ckpt 2424: BI8 added (Tj: Bids tab always shown; turning bids/auto-make on turns on Vig
  58d0876a ckpt 2423: BI: Tj's 6 optimizations (diagnostics, wallet always visible, scan lag, Bids
  9ada3d93 ckpt 2422: BH done: v0.52.0 released + recorded (maker audit fixes, +EV-only invariant,
  4a4a5b6a ckpt 2421: pre-release: v0.52.0: make orders only +EV and never older than their fair (
  2e6790e6 ckpt 2420: pre-ship: v0.52.0: make orders only +EV and never older than their fair (exp
  46b57f5f ckpt 2419: BH1-BH6 done: maker audit fixes (6 bugs), books agree + sharp veto + Kelly, 
  597acd33 ckpt 2418: BH1-3 data fixes: cancels confirmed (CANCELING), fills read even on 404 / lo
  e8755330 ckpt 2417: BH0 done: v0.51.0 released + recorded (BG6 ticked)
  622feada ckpt 2416: BH: Tj's make-orders follow-up written to TASKS.md (full tests, +EV only, ti
  94cdbdc2 ckpt 2415: pre-release: v0.51.0: make orders (the Bids tab): post-only bids under Vigil
```

(8 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
