# CHECKPOINT 597 — read me first, then TASKS.md

**Written:** 2026-09-28T19:58:35Z · **tests:** all 1 fast checks green
**Branch:** `ccr-ed1962c6-kshrww` · **builds on:** `2d7374f` (this checkpoint is the commit after it)

## Just done
pre-release: v0.19.5: pause all scanning (Settings switch, pause/resume on the +EV and CNO tabs and the widget: stops a running scan, CNO's reads, background auto-scan and widget rescans until you resume); one-tap Open in Novig on every CNO tab card, with the bet-slip stake (versionCode 40, v0.19.5)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.19.5), then run: bash tools/record-release.sh v0.19.5 40 "v0.19.5: pause all scanning (Settings switch, pause/resume on the +EV and CNO tabs and the widget: stops a running scan, CNO's reads, background auto-scan and widget rescans until you resume); one-tap Open in Novig on every CNO tab card, with the bet-slip stake"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  2d7374f ckpt 596: U1 done + full floor 752/0 failures; v0.19.5 (40) ready
  bf7f8d1 ckpt 595: U1 built: pause all scanning (ScanSettings.paused, CnoWatch.hold, ScanRunner.s
  25f6d90 ckpt 594: U2: Open in Novig button on every CNO tab card (same path/stake as the widget 
  a021c70 ckpt 593: Recorded Tj's request (pause all scanning; Open in Novig buttons on CNO tab ca
  090d751 ckpt 592: Released Vigilant v0.19.4 (code 39): release.yml green on 23c4b10, vigilant-v0
  23c4b10 ckpt 591: pre-release: v0.19.4: Novig prices per scan up to 2,000 (long scans skip lines
  216f4ba ckpt 590: T7 pre-ship: fixed flaky NovigPublicClientTest 'a refused wave is waited out o
  43f033e ckpt 589: T1-T3: budget up to 2,000 + too-late read guard (no 'No limit', reasons in RES
  0a4c486 ckpt 588: T4 + T6: bet-slip amount setting (Off/$1/Kelly/My amount) on every Novig link 
  d27d061 ckpt 587: T5: PinnWire->pinnapi was built (daily limit); pinned the reset cycle with a t
```
