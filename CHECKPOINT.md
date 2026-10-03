# CHECKPOINT 2499 — read me first, then TASKS.md

**Written:** 2026-10-03T23:24:50Z · **tests:** all 3 fast checks green
**Branch:** `ccr-e9ab2595-yxxu6w` · **builds on:** `fcbd3e13` (this checkpoint is the commit after it)

## Just done
pre-release: v0.58.2: the scan study is finished and written inside every background auto-scan cycle (and when its Vigilant scan ends), so alarm-only mode loses nothing; includes the props split (sharp-book verdict splits and what-if lines) (versionCode 102, v0.58.2)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.58.2), then run: bash tools/record-release.sh v0.58.2 102 "v0.58.2: the scan study is finished and written inside every background auto-scan cycle (and when its Vigilant scan ends), so alarm-only mode loses nothing; includes the props split (sharp-book verdict splits and what-if lines)"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  fcbd3e13 ckpt 2498: BT done in code: StudySync.catchUp in the background cycle and at a scan's e
  9ad7aeaa ckpt 2497: BT: Tj asks to confirm the study logs everything in background auto-scan mod
  1a8aa7f0 ckpt 2496: BS1/BS2 built: props splits + what-if lines in the study file; v0.58.1 (code
  d68bde0b ckpt 2495: BR3 answered: Tj picked option 1 (leave rules, add props split to the study)
  bcca9206 ckpt 2494: v0.58.0 released + recorded (release.yml run 37159279383 green, tag v0.58.0)
  deaaacb3 ckpt 2493: pre-release: v0.58.0: the scan study also logs what CNO's filters hide (a se
  162485c7 ckpt 2492: BQ done in code: v0.58.0 (code 100) prepared; floor green (1,919 passed, 23 
  698f3b3c ckpt 2491: BQ2 part 2: study wide path (w/xw sights, NOT_LISTED, cols, lean hydrate+fol
  df2afb76 ckpt 2490: BQ2 part 1: CnoClient.fetchWide (own session, opened filters, columns kept),
  9c963c5a ckpt 2489: BQ1 done: wide-read design (second CNO session, w sights, cols, NOT_LISTED) 
```
