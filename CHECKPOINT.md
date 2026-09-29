# CHECKPOINT 1996 — read me first, then TASKS.md

**Written:** 2026-09-29T00:19:45Z · **tests:** all 1 fast checks green
**Branch:** `ccr-690067b2-r33qn5` · **builds on:** `beac0b95` (this checkpoint is the commit after it)

## Just done
X2-X3 done: Chris Banes' four Compose/coroutine skills in .claude/skills (unchanged, license + notices, vetted); PROMPT_AUDIT.md/RESEARCH §33 notes updated; audit patch still applies

## Do this next
Wait for Tj to pick audit fixes (A1-A11, B1-B10; flags F1-F11 are his decisions); then apply the chosen hunks from PROMPT_AUDIT.patch, run tools/test_resume.sh, delete PROMPT_AUDIT.md/.patch

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M TASKS.md

## Last ten checkpoints
```
  f3085ec4 ckpt 1992: X1 done: prompt audit report PROMPT_AUDIT.md + PROMPT_AUDIT.patch (21 propos
  df0cd971 ckpt 608: Recorded Tj's request (run the prompt audit, add the Compose skills) as X1-X3
  7c12e385 ckpt 607: W1-W4 done: RESEARCH.md §33 complete (claude-api skill/hillclimb verdicts, b
  2854a6b9 ckpt 606: W1/W2 researched, RESEARCH.md §33.1-33.4 written (claude-api skill + hillcli
  5b8b0a06 ckpt 605: W4 (part): session-start briefing fixed — it was 215 KB, over Claude Code's
  c18df1d7 ckpt 604: Recorded Tj's request (research claude-api skill + hillclimb, other skills/pl
  5c4ca421 ckpt 603: Released Vigilant v0.19.6 (code 41): release.yml green on 036f664, APK verifi
  036f6644 ckpt 602: pre-release: v0.19.6: No limit on every scan cap (Novig prices per scan, prop
  87fa0ef5 ckpt 601: V1-V5 built (No limit/All on every scan cap, PropLine games setting, scan win
  189fc62c ckpt 600: V2-V5 data side: ScanSettings.NO_LIMIT on Novig prices/credits/lines/props/pr
```

(3 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
