# CHECKPOINT 2891 — read me first, then TASKS.md

**Written:** 2026-10-10T18:25:56Z · **tests:** all 4 fast checks green
**Branch:** `ccr-3fba2f23-lerg26` · **builds on:** `609a86de` (this checkpoint is the commit after it)

## Just done
pre-release: v0.88.4: feed race counts one sample per update (not per market), so the lag numbers are not inflated by one update rewriting thirty markets (versionCode 177, v0.88.4)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.88.4), then run: bash tools/record-release.sh v0.88.4 177 "v0.88.4: feed race counts one sample per update (not per market), so the lag numbers are not inflated by one update rewriting thirty markets"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  45d02b7c ckpt 2890: pre-release: v0.88.3: Pinnacle compare run says whether the socket and the w
  54922bf6 ckpt 2889: pre-release: v0.88.2: Pinnacle website compare run shows what its own book r
  a7a8121a ckpt 2888: pre-release: v0.88.1: Pinnacle website feed treats a 403 among answers as th
  0b82c1a0 ckpt 2887: TN5 done: trial-replacement research (research file, RESEARCH 128); TN6-TN9 
  258ee08b ckpt 2886: v0.88.0 released+recorded
  e3b3b1d1 ckpt 2885: pre-release: v0.88.0: Pinnacle website feed (free) as a choice beside the Pi
  7aa68f72 ckpt 2884: TN2/TN3 built: website feed wired (feed choice, compare race, UI, diagnostic
  f9e16fc5 ckpt 2883: TN2 core: PinnWebsiteFeed + VersionRace + PinnWebsiteSettings + PinnBook.ver
  5ce7c2e5 ckpt 2882: TN written (Pinnacle website feed + trial replacement research)
  c703ca54 ckpt 2881: v0.87.1 released+recorded; TM research done
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
