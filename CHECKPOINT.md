# CHECKPOINT 2615 — read me first, then TASKS.md

**Written:** 2026-10-06T04:18:57Z · **tests:** all 3 fast checks green
**Branch:** `ccr-3436e911-cyln4u` · **builds on:** `bae94540` (this checkpoint is the commit after it)

## Just done
Tj sent two files (v0.70.1 diagnostics + scan study, no words): data extracted, shared loader + extractor + workflow script saved in tools/research/study_v0701/, resume file research/scan_study_analysis_2026-10-06_v0.70.1_checkpoint.md written, TASKS CX1-CX3 written; the 14-agent analysis workflow wf_47e30351-756 was launched at 04:14Z and has NOT finished (results go only to the launching session)

## Do this next
READ research/scan_study_analysis_2026-10-06_v0.70.1_checkpoint.md FIRST and follow its HOW TO RESUME steps 1-8 (check the old workflow's journal.jsonl for finished results; else re-extract with tools/research/study_v0701/extract.py and relaunch workflow_files_analysis.js; ask Tj to resend the two files only if /root/.claude/uploads is empty); then v0.70.2 fixes for app faults (incl. always print the burst recorder state in the diagnostics), RESEARCH 97, short-bullet answer + Release link; rule changes are proposals to Tj, never done unasked

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  1909ebb8 ckpt 2614: v0.70.1 RELEASED and recorded (v0.70.0 too); RESEARCH 95/96 and NOVIG_API 20
  d4810416 ckpt 2613: pre-release: v0.70.1: the burst trader is judged league by league (a league 
  4d6b4374 ckpt 2612: v0.70.1: per-league proof for the trader (a league trades only on its own pr
  73c148b8 ckpt 2611: pre-release: v0.70.0: real-money burst trader behind the recorder (OFF by de
  ccff3d0c ckpt 2610: executor done: full floor 2,289 passed (23 skipped); TASKS CV1 ticked, NOVIG
  0cad4cb3 ckpt 2609: burst trader wired into the app (adapter, rules, gate from BurstTradeGate + 
  a37f24dd ckpt 2608: v0.69.0 released + recorded (run green, Release up); BurstTrader core + 16 t
  d561774b ckpt 2607: pre-release: v0.69.0: live burst recorder (no orders): watches live games' l
  5a183237 ckpt 2606: v0.69.0 burst recorder: full floor green (2,282), TASKS/NOVIG_API updated, v
  46753e50 ckpt 2605: score-burst recorder built: ladders, covers, windows, paper trader, delays, 
```

(6 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
