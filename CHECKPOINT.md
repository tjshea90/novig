# CHECKPOINT 2725 — read me first, then TASKS.md

**Written:** 2026-10-07T23:50:01Z · **tests:** all 4 fast checks green
**Branch:** `ccr-f8e1d0b1-qlnoqa` · **builds on:** `c55f2828` (this checkpoint is the commit after it)

## Just done
Made the ten-sources research resumable by any session/account: tools/research/save_workflow.py (saver v2: results/<label>.json per finished agent, FINDINGS.md, raw journal, partial/<label>.md traces of in-flight agents every ~90 s, STATUS.md, key redaction + secret scan, banks within ~10 s, only that folder) is running; tools/research/rapid_sources_workflow.js (resumable script, args.skip) and tools/research/rapid_resume.py committed; research/rapid_sources_workflow_2026-10-07/RESUME.md written; DO1 in TASKS.md points to it

## Do this next
IF CUT OFF: new session reads research/rapid_sources_workflow_2026-10-07/RESUME.md: python3 tools/research/rapid_resume.py, Workflow scriptPath tools/research/rapid_sources_workflow.js args {skip:[...]}, start the saver with the new transcript dir; when results/final.json exists write RESEARCH.md section 115, tick DO1-DO3, answer Tj (one line per source) and ask DL2 again. IF NOT CUT OFF: wait for the workflow notification (wf_6f04c078-2f9), then the same last step

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  3fa3c3f6 ckpt 2724: Tj's request (ten sources: are any a rapid odds/scores source, cheap or free
  f996d0b2 ckpt 2723: v0.75.0 RELEASED and recorded (https://github.com/tjshea90/novig/releases/ta
  38267e0b ckpt 2722: pre-release: v0.75.0: bids can be priced from CrazyNinjaOdds alone, Vigilant
  6a80d377 ckpt 2721: DM4: 23 mutants killed (age rule, orientation, sharp requirement, unknown ag
  2b31e45d ckpt 2720: DM3c done: Bids tab (Bids priced from chip, CNO age limit chips, CNO status 
  04317943 ckpt 2719: DM3b done: CnoBidLane (data: wide read + page lane with a 3-page/step budget
  f555eb44 ckpt 2718: DM3a done: BidSource + makerSource + makerCnoMaxAgeSeconds + bidsFromCno in 
  886a28fc ckpt 2717: DM1 done: wrote RESEARCH.md §113 (verdict: CNO prices are already old when 
  75aa246a ckpt 2716: DM1 findings so far written into TASKS.md (one page-wide CNO age, missing ag
  c4b02728 ckpt 2715: Saved the first 6 of 7 scout results of research workflow wf_edc6b432-17e to
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
