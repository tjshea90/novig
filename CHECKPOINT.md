# CHECKPOINT 2888 — read me first, then TASKS.md

**Written:** 2026-10-10T17:37:57Z · **tests:** all 4 fast checks green
**Branch:** `ccr-3fba2f23-lerg26` · **builds on:** `db9e6a35` (this checkpoint is the commit after it)

## Just done
pre-release: v0.88.1: Pinnacle website feed treats a 403 among answers as throttling (slows, no false key alarm), polls the score less often, race shows how many versions each feed reported (versionCode 174, v0.88.1)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.88.1), then run: bash tools/record-release.sh v0.88.1 174 "v0.88.1: Pinnacle website feed treats a 403 among answers as throttling (slows, no false key alarm), polls the score less often, race shows how many versions each feed reported"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  0b82c1a0 ckpt 2887: TN5 done: trial-replacement research (research file, RESEARCH 128); TN6-TN9 
  258ee08b ckpt 2886: v0.88.0 released+recorded
  e3b3b1d1 ckpt 2885: pre-release: v0.88.0: Pinnacle website feed (free) as a choice beside the Pi
  7aa68f72 ckpt 2884: TN2/TN3 built: website feed wired (feed choice, compare race, UI, diagnostic
  f9e16fc5 ckpt 2883: TN2 core: PinnWebsiteFeed + VersionRace + PinnWebsiteSettings + PinnBook.ver
  5ce7c2e5 ckpt 2882: TN written (Pinnacle website feed + trial replacement research)
  c703ca54 ckpt 2881: v0.87.1 released+recorded; TM research done
  98ae1984 ckpt 2880: TM1-TM2 done: odds sources research written (research file, RESEARCH §126, 
  567a7dd2 ckpt 2879: TM request written (odds source research)
  84153559 ckpt 2878: pre-release: v0.87.1: live tail bettor (decided games, no Pinnacle price nee
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
