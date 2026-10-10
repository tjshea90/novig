# CHECKPOINT 2885 — read me first, then TASKS.md

**Written:** 2026-10-10T17:05:06Z · **tests:** all 4 fast checks green
**Branch:** `ccr-3fba2f23-lerg26` · **builds on:** `cdc4caa6` (this checkpoint is the commit after it)

## Just done
pre-release: v0.88.0: Pinnacle website feed (free) as a choice beside the Pinnodds socket, with a compare race that times it against the socket (versionCode 173, v0.88.0)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.88.0), then run: bash tools/record-release.sh v0.88.0 173 "v0.88.0: Pinnacle website feed (free) as a choice beside the Pinnodds socket, with a compare race that times it against the socket"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  7aa68f72 ckpt 2884: TN2/TN3 built: website feed wired (feed choice, compare race, UI, diagnostic
  f9e16fc5 ckpt 2883: TN2 core: PinnWebsiteFeed + VersionRace + PinnWebsiteSettings + PinnBook.ver
  5ce7c2e5 ckpt 2882: TN written (Pinnacle website feed + trial replacement research)
  c703ca54 ckpt 2881: v0.87.1 released+recorded; TM research done
  98ae1984 ckpt 2880: TM1-TM2 done: odds sources research written (research file, RESEARCH §126, 
  567a7dd2 ckpt 2879: TM request written (odds source research)
  84153559 ckpt 2878: pre-release: v0.87.1: live tail bettor (decided games, no Pinnacle price nee
  5ed847be ckpt 2877: TLc tail taker built+wired, full floor green (2993), RESEARCH §125 addendum
  4e8e4813 ckpt 2876: v0.87.0 released+recorded; TailTaker (data) + LabRecorder onTail done, tests
  71d30dea ckpt 2875: pre-release: v0.87.0: live autopilot (taker + bids together), More fills pre
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
