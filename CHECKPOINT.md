# CHECKPOINT 535 — read me first, then TASKS.md

**Written:** 2026-09-27T21:50:02Z · **tests:** all 1 fast checks green
**Branch:** `claude/novig-game-time-filter-tkml0n` · **builds on:** `81f7f22` (this checkpoint is the commit after it)

## Just done
H4 in progress: floor 623 green + live Novig/CNO/scores green; fixed F1 Recheck re-read hidden bets (feedMarketIds -> feedAt; StartsWithinTest 'Recheck re-reads only the bets shown', fails pre-fix) and F2 a11y: sort + window buttons expose selected (ScreenshotTest.theFeedCanShowOnlyGamesStartingSoon, fails pre-fix)

## Do this next
H4: continue sweep (settings copy, tracker, widgets), write improvement list, full regression, ship v0.17.1 Vigilant only

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  1be888c ckpt 534: H3: Maven Central 429 research + tools/setup-android.sh (SDK, Gradle mirror, R
  9e9482f ckpt 533: H2: Starts-within picker on the CNO tab + widget top bar (cycles), counts of w
  b50cc33 ckpt 532: H1: Vigilant MGM dormant: CLAUDE.md/BRIEF.md standing rule, :mgm only with -Pm
  2309be8 ckpt 531: Wrote Tj's 4 requests into TASKS.md (H1-H4); held v0.17.1's release (deleted t
  fb0f8bb ckpt 530: v0.17.1 shipped to main via ship.sh (619 tests green locally); CI run 36351838
  0936ecc ckpt 529: pre-release: v0.17.1: 'Starts within' filter in Vigilant (Any time / 12h / 24h
  80596f1 ckpt 528: G1+G2: start-time window (Any/12/24/48h) on feed, CNO, Games, widgets, notific
  5d4dc1d ckpt 527: Wrote Tj's game start-time filter request into TASKS.md (G1-G3)
  0974b60 ckpt 526: SHIPPED v0.17.0 code 32 (V1-V5 ticked): Vigilant MGM + Vigilant both on https:
  bbd955f ckpt 525: pre-release: v0.17.0: Vigilant MGM, the same +EV scanner for BetMGM as its own
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
