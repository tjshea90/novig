# CHECKPOINT 2883 — read me first, then TASKS.md

**Written:** 2026-10-10T16:57:25Z · **tests:** all 4 fast checks green
**Branch:** `ccr-3fba2f23-lerg26` · **builds on:** `c794ea08` (this checkpoint is the commit after it)

## Just done
TN2 core: PinnWebsiteFeed + VersionRace + PinnWebsiteSettings + PinnBook.versionListener, 8 tests green

## Do this next
wire into VigilantApp (openFeed choice, shadow compare, key gating), Pinnodds live page UI, diagnostics, tests, ship v0.88.0

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M data/src/main/kotlin/com/tjshea/vigilant/data/pinnodds/PinnWebsiteFeed.kt
     M data/src/test/kotlin/com/tjshea/vigilant/data/pinnodds/PinnWebsiteFeedTest.kt

## Last ten checkpoints
```
  5ce7c2e5 ckpt 2882: TN written (Pinnacle website feed + trial replacement research)
  c703ca54 ckpt 2881: v0.87.1 released+recorded; TM research done
  98ae1984 ckpt 2880: TM1-TM2 done: odds sources research written (research file, RESEARCH §126, 
  567a7dd2 ckpt 2879: TM request written (odds source research)
  84153559 ckpt 2878: pre-release: v0.87.1: live tail bettor (decided games, no Pinnacle price nee
  5ed847be ckpt 2877: TLc tail taker built+wired, full floor green (2993), RESEARCH §125 addendum
  4e8e4813 ckpt 2876: v0.87.0 released+recorded; TailTaker (data) + LabRecorder onTail done, tests
  71d30dea ckpt 2875: pre-release: v0.87.0: live autopilot (taker + bids together), More fills pre
  e022e1ef ckpt 2874: TL Phase A UI+tests green; RESEARCH §125 written
  1a98f6da ckpt 2873: TL3 Phase A core: FILL preset, fillWallet limits, EITHER trigger, LiveAutopi
```

(8 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
