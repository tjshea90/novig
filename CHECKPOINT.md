# CHECKPOINT 539 — read me first, then TASKS.md

**Written:** 2026-09-27T21:58:04Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-game-time-filter-tkml0n` · **builds on:** `ba12b55` (this checkpoint is the commit after it)

## Just done
PAUSED for Tj's model switch. H1-H4 all done and on main (v0.17.1 code 33 gated by ship.sh at 3a66035). The first release run was cancelled (triggered before CI finished; no tag/Release). Tj re-sent the same H1-H4 message: nothing new.

## Do this next
Release v0.17.1 only: confirm ci.yml green on main's head commit (check with the GitHub API, don't assume), then trigger release.yml on main, confirm tag v0.17.1 carries vigilant-v0.17.1.apk ONLY, bash tools/record-release.sh v0.17.1 33 "v0.17.1: 'Starts within' filter (Any time / 12h / 24h / 48h) on the +EV tab, CNO tab, floating widget and Settings; every list, badge and notification shows only games starting in that window. Recheck re-reads only the bets shown. Vigilant only from now on (Vigilant MGM dormant at v0.17.0). 624 tests", tick G3, send Tj the Release link (plain text)

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  4043306 ckpt 538: Shipped v0.17.1 to main (3a66035); release.yml run 36353475955 CANCELLED at th
  3a66035 ckpt 537: pre-release: v0.17.1: 'Starts within' filter (Any time / 12h / 24h / 48h) on t
  f2a534d ckpt 536: H4 full tests done: 624 green, live green, release APK verified; fixes F1 (Rec
  078fd3a ckpt 535: H4 in progress: floor 623 green + live Novig/CNO/scores green; fixed F1 Rechec
  1be888c ckpt 534: H3: Maven Central 429 research + tools/setup-android.sh (SDK, Gradle mirror, R
  9e9482f ckpt 533: H2: Starts-within picker on the CNO tab + widget top bar (cycles), counts of w
  b50cc33 ckpt 532: H1: Vigilant MGM dormant: CLAUDE.md/BRIEF.md standing rule, :mgm only with -Pm
  2309be8 ckpt 531: Wrote Tj's 4 requests into TASKS.md (H1-H4); held v0.17.1's release (deleted t
  fb0f8bb ckpt 530: v0.17.1 shipped to main via ship.sh (619 tests green locally); CI run 36351838
  0936ecc ckpt 529: pre-release: v0.17.1: 'Starts within' filter in Vigilant (Any time / 12h / 24h
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
