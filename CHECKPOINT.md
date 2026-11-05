# CHECKPOINT 4392 — read me first, then TASKS.md

**Written:** 2026-11-05T22:24:38Z · **tests:** all 10 fast checks green
**Branch:** `ccr-c53b8db3-i98qqw` · **builds on:** `79b213ce6` (this checkpoint is the commit after it)

## Just done
NC1j: live check of NcaabBoard against Novig's real NCAAB catalog (82 games) found a lopsided 'main' number: near-even rule added; phone reads Novig itself when the engine isn't serving the board (engine was idle: empty wallet); tests 13+14 green

## Do this next
ship: bash ship.sh (full gate), release.yml, record-release.sh; then NC1i once the engine runs a scan with NCAAB in leagues

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M CLAUDE.md
     M TASKS.md

## Last ten checkpoints
```
  6226b4740 ckpt 4391: Wrong-repo session: Tj attached the Launcher handoff here by mistake, no no
  52e2b2556 ckpt 4390: ANALYST 11-05: no change, note written
  0a377cc23 ckpt 4389: WATCH 11-05: engine healthy, nothing shipped
  03de75b0c ckpt 4388: SHIPPED v1.0.102 (code 442): NC1 college basketball ATS tab + GitHub game l
  9832ff28b ckpt 4387: pre-release: NC1: NCAAB tab (college basketball ATS system with Novig lines
  6ba1b8ed0 ckpt 4386: NC1 review fixes: ncaab-log.yml (data/ collision with the Gradle module + f
  8033d8408 ckpt 4385: RESUME as acct-27a37e (tj Gmail): merged acct-7542bf's unlanded NC1 branch 
  13c79d3a9 ckpt 4384: NC1: parlay builder (NovigParlay + ParlayBar), research note, NOVIG_API.md 
  fb9b99b9f ckpt 4383: NC1: engine publishes ncaab.json.gz (stageNcaab), CloudLink/CloudClient fet
  876c1cb1b ckpt 4382: NC1: ncaab-log.yml workflow + 3-season seed (17,323 games) + backtest repor
```

(5 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
