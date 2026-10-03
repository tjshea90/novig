# CHECKPOINT 2421 — read me first, then TASKS.md

**Written:** 2026-10-03T03:32:47Z · **tests:** all 3 fast checks green
**Branch:** `ccr-91942f39-sfdkh9` · **builds on:** `2e6790e6` (this checkpoint is the commit after it)

## Just done
pre-release: v0.52.0: make orders only +EV and never older than their fair (expiry bounded by the fair's freshness), confirmed cancels, books agree + sharp veto + Kelly sizing, auto-make like auto-bet, approve/deny recommendations (tab + notifications) (versionCode 92, v0.52.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.52.0), then run: bash tools/record-release.sh v0.52.0 92 "v0.52.0: make orders only +EV and never older than their fair (expiry bounded by the fair's freshness), confirmed cancels, books agree + sharp veto + Kelly sizing, auto-make like auto-bet, approve/deny recommendations (tab + notifications)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  2e6790e6 ckpt 2420: pre-ship: v0.52.0: make orders only +EV and never older than their fair (exp
  46b57f5f ckpt 2419: BH1-BH6 done: maker audit fixes (6 bugs), books agree + sharp veto + Kelly, 
  597acd33 ckpt 2418: BH1-3 data fixes: cancels confirmed (CANCELING), fills read even on 404 / lo
  e8755330 ckpt 2417: BH0 done: v0.51.0 released + recorded (BG6 ticked)
  622feada ckpt 2416: BH: Tj's make-orders follow-up written to TASKS.md (full tests, +EV only, ti
  94cdbdc2 ckpt 2415: pre-release: v0.51.0: make orders (the Bids tab): post-only bids under Vigil
  56e1fac1 ckpt 2414: pre-ship: v0.51.0: make orders (the Bids tab): post-only bids under Vigilant
  e79c9c9b ckpt 2413: BG6e done: Bids tab + MakerRunner + container wiring + Diagnostics + Tracker
  6e894597 ckpt 2412: BG6e in progress: MakerRunner (container: desk, after-scan pass, cancel on o
  9fa8c34e ckpt 2411: BG6a-d done: client ttl/cancel, maker settings, MakerQuote/MakerPlan/MakerLi
```
