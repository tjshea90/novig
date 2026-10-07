# CHECKPOINT 2687 — read me first, then TASKS.md

**Written:** 2026-10-07T12:09:52Z · **tests:** all 4 fast checks green
**Branch:** `ccr-b67c8afa-h14i94` · **builds on:** `4db49a3e` (this checkpoint is the commit after it)

## Just done
pre-release: v0.72.2: a preset now carries the auto-bet's favourite bar; preset summaries and the auto-bet criteria say a plus-money limit means underdogs only; the favourite note counts in points; API audit and full-tests write-up (RESEARCH 107) (versionCode 128, v0.72.2)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.72.2), then run: bash tools/record-release.sh v0.72.2 128 "v0.72.2: a preset now carries the auto-bet's favourite bar; preset summaries and the auto-bet criteria say a plus-money limit means underdogs only; the favourite note counts in points; API audit and full-tests write-up (RESEARCH 107)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  4db49a3e ckpt 2686: DH4 + DH5 done: RESEARCH 107, preset favourite bar, plus-money and favourite
  7e0563fd ckpt 2685: pre-release: v0.72.1: live feed test (opt-in, reads only, no orders) for sco
  1ce250f7 ckpt 2684: v0.72.1 candidate: live feed test in the app (off by default), batch reply a
  bdb94219 ckpt 2683: DH2 app side: live feed test (ScanSettings.feedRace OFF by default, Vigilant
  686eb39e ckpt 2682: DH2: FeedRaceRunner (every feed read only while Novig has a live game it cov
  13f7cc75 ckpt 2681: DH2: FeedParsers (Sofascore, Polymarket score+odds, ESPN, NHL, MLB) and Feed
  04d0cb5b ckpt 2680: v0.72.0 released and recorded; DH2 first measured numbers (tennis tape, RESE
  4eee78e0 ckpt 2679: DH3: batch-place reply array shape fixed (4 occurrences in Tj's file), DoH c
  7ff1919e ckpt 2678: pre-release: v0.72.0: small-market bid fill behind the popular bids (Quick &
  92453213 ckpt 2677: v0.72.0 candidate complete: obscure fill + rec 11 + recs 2/4/5/9/10, RESEARC
```
