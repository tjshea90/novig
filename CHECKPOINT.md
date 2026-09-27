# CHECKPOINT 537 — read me first, then TASKS.md

**Written:** 2026-09-27T21:54:55Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-game-time-filter-tkml0n` · **builds on:** `f2a534d` (this checkpoint is the commit after it)

## Just done
pre-release: v0.17.1: 'Starts within' filter (Any time / 12h / 24h / 48h) on the +EV tab, CNO tab, floating widget and Settings; every list, badge and notification shows only games starting in that window. Recheck re-reads only the bets shown. Vigilant only from now on (Vigilant MGM dormant at v0.17.0). 624 tests (versionCode 33, v0.17.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.17.1), then run: bash tools/record-release.sh v0.17.1 33 "v0.17.1: 'Starts within' filter (Any time / 12h / 24h / 48h) on the +EV tab, CNO tab, floating widget and Settings; every list, badge and notification shows only games starting in that window. Recheck re-reads only the bets shown. Vigilant only from now on (Vigilant MGM dormant at v0.17.0). 624 tests"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  f2a534d ckpt 536: H4 full tests done: 624 green, live green, release APK verified; fixes F1 (Rec
  078fd3a ckpt 535: H4 in progress: floor 623 green + live Novig/CNO/scores green; fixed F1 Rechec
  1be888c ckpt 534: H3: Maven Central 429 research + tools/setup-android.sh (SDK, Gradle mirror, R
  9e9482f ckpt 533: H2: Starts-within picker on the CNO tab + widget top bar (cycles), counts of w
  b50cc33 ckpt 532: H1: Vigilant MGM dormant: CLAUDE.md/BRIEF.md standing rule, :mgm only with -Pm
  2309be8 ckpt 531: Wrote Tj's 4 requests into TASKS.md (H1-H4); held v0.17.1's release (deleted t
  fb0f8bb ckpt 530: v0.17.1 shipped to main via ship.sh (619 tests green locally); CI run 36351838
  0936ecc ckpt 529: pre-release: v0.17.1: 'Starts within' filter in Vigilant (Any time / 12h / 24h
  80596f1 ckpt 528: G1+G2: start-time window (Any/12/24/48h) on feed, CNO, Games, widgets, notific
  5d4dc1d ckpt 527: Wrote Tj's game start-time filter request into TASKS.md (G1-G3)
```
