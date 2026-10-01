# CHECKPOINT 2238 — read me first, then TASKS.md

**Written:** 2026-10-01T04:01:18Z · **tests:** all 3 fast checks green
**Branch:** `ccr-650fc40f-rtt4er` · **builds on:** `05d8e4cd` (this checkpoint is the commit after it)

## Just done
AA2/AA3: at-bet no longer a close, final read ~110 s before start (needsFinalRead), close only moves to a fresher read (supersedes), fast-cycle closing reads (closingFreshMs); ClosingLineTest, ClvPlacedPriceTest, BothReadsTest green

## Do this next
run app suites (ClosingLineAppTest, TrackerTextTest, DiagnosticsTest, HealthChecks), fix fallout, then ScreenshotTest settings row, light protocol, docs, ship v0.38.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  b86607f3 ckpt 2237: AA1+AA2 core: autoScanSeconds (15/30/60/180 s + 5-40 min, schema 12 migratio
  5a61e310 ckpt 2236: AA: wrote Tj's auto-scan interval + CLV request into TASKS.md as AA1-AA5 wit
  bfd74452 ckpt 2235: v0.37.0 (code 67) released and recorded: Z1-Z5 done (tennis through ParlayAP
  c94e2078 ckpt 2234: pre-release: v0.37.0: tennis through ParlayAPI (bet365, Caesars, DraftKings,
  11118acf ckpt 2233: pre-ship: v0.37.0: tennis through ParlayAPI (bet365, Caesars, DraftKings, Be
  da21ef82 ckpt 2232: light test (Z4): who-else check clean, screenshot 5h_settings_fair_parlay_on
  11541887 ckpt 2231: Z3: ParlayBooks tennis (units + 1-day gap), ParlayCloses tennis closes in th
  2eec4225 ckpt 2230: Z2 done + live-verified: 63/64 Novig tennis matches paired via ParlayAPI, SE
  46adcb27 ckpt 2229: Z2 core: ParlayTennis.normalize (doubles dropped, (Games) twin + other books
  18cce014 ckpt 2228: Z1: real ParlayAPI tennis read (10 credits): Pinnacle match event = set line
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
