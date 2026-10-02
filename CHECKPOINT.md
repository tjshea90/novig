# CHECKPOINT 2286 — read me first, then TASKS.md

**Written:** 2026-10-02T00:25:33Z · **tests:** all 3 fast checks green
**Branch:** `ccr-650fc40f-rtt4er` · **builds on:** `4badc0d5` (this checkpoint is the commit after it)

## Just done
pre-release: v0.42.0: auto-bet and background auto-scan switch themselves off every time Vigilant is closed and opened again (a fresh launch), with a note on screen; and a sharp-book confirmation (Settings › Betting, off by default) for auto-bet and CNO's push alerts: a fresh Pinnacle price (PinnWire/pinnapi, PropLine or ParlayAPI feed with the quote's own time, or CNO's game page) for the exact same line and side, devigged worst case, must show +EV at Novig's price now (1 to 5 minutes old at most, any +EV to +3%), and a sharp book that says no vetoes; asked last, for a bet about to be placed, with CNO's page as a free veto (versionCode 77, v0.42.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.42.0), then run: bash tools/record-release.sh v0.42.0 77 "v0.42.0: auto-bet and background auto-scan switch themselves off every time Vigilant is closed and opened again (a fresh launch), with a note on screen; and a sharp-book confirmation (Settings › Betting, off by default) for auto-bet and CNO's push alerts: a fresh Pinnacle price (PinnWire/pinnapi, PropLine or ParlayAPI feed with the quote's own time, or CNO's game page) for the exact same line and side, devigged worst case, must show +EV at Novig's price now (1 to 5 minutes old at most, any +EV to +3%), and a sharp book that says no vetoes; asked last, for a bet about to be placed, with CNO's page as a free veto"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  4badc0d5 ckpt 2285: mutation checks done (sharp rules, feeds, gate, wiring, LaunchReset); docs R
  c510206c ckpt 2284: AL1 built (LaunchReset); AL4 built: SharpConfirm rules, SharpBooks feeds, Sh
  5d05a967 ckpt 2283: wrote Tj's reopen-resets and sharp-book confirmation request into TASKS.md a
  6aa592f3 ckpt 2282: v0.41.0 released and recorded: Keep awake for auto-scan/auto-bet with the sc
  ce28edc3 ckpt 2281: pre-release: v0.41.0: Keep awake keeps background auto-scan and auto-bet on 
  737304b0 ckpt 2280: AK3 built and mutation-checked: KeepAwake rules, CycleLog meter, service loo
  6a8935c1 ckpt 2279: AK3 in progress: KeepAwake rules, CycleLog, AutoScanService loop + watchdog,
  f79ef723 ckpt 2278: wrote Tj's keep-alive research request into TASKS.md as AK1-AK4
  56129545 ckpt 2277: v0.40.2 released and recorded: auto-bet every-book-must-agree switch (AJ1-AJ
  8f849aa9 ckpt 2276: pre-release: v0.40.2: auto-bet can require that every book scanned agrees th
```
