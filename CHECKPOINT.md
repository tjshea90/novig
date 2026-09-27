# CHECKPOINT 538 — read me first, then TASKS.md

**Written:** 2026-09-27T21:57:19Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-game-time-filter-tkml0n` · **builds on:** `3a66035` (this checkpoint is the commit after it)

## Just done
Shipped v0.17.1 to main (3a66035); release.yml run 36353475955 CANCELLED at the APK build (it was triggered before CI finished; no tag, no Release)

## Do this next
When CI 36353437186 on 3a66035 is really completed+success: trigger release.yml on main, confirm tag v0.17.1 (vigilant-v0.17.1.apk only), bash tools/record-release.sh v0.17.1 33 "<ship note>", tick G3/H4 ship, send Tj the link

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  3a66035 ckpt 537: pre-release: v0.17.1: 'Starts within' filter (Any time / 12h / 24h / 48h) on t
  f2a534d ckpt 536: H4 full tests done: 624 green, live green, release APK verified; fixes F1 (Rec
  078fd3a ckpt 535: H4 in progress: floor 623 green + live Novig/CNO/scores green; fixed F1 Rechec
  1be888c ckpt 534: H3: Maven Central 429 research + tools/setup-android.sh (SDK, Gradle mirror, R
  9e9482f ckpt 533: H2: Starts-within picker on the CNO tab + widget top bar (cycles), counts of w
  b50cc33 ckpt 532: H1: Vigilant MGM dormant: CLAUDE.md/BRIEF.md standing rule, :mgm only with -Pm
  2309be8 ckpt 531: Wrote Tj's 4 requests into TASKS.md (H1-H4); held v0.17.1's release (deleted t
  fb0f8bb ckpt 530: v0.17.1 shipped to main via ship.sh (619 tests green locally); CI run 36351838
  0936ecc ckpt 529: pre-release: v0.17.1: 'Starts within' filter in Vigilant (Any time / 12h / 24h
  80596f1 ckpt 528: G1+G2: start-time window (Any/12/24/48h) on feed, CNO, Games, widgets, notific
```
