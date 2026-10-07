# RESUME: the ten-sources research (Tj, 2026-10-07)

**The question** (Tj, in his own words, INBOX.md 2026-10-07T23:29Z): "Research each of the following sources to see if a rapid source of odds or scores can be built or if any of
the apis can be used for rapid odds or scores either cheap or free" (ten links: TASKS.md, section "Tj, 2026-10-07 (~23:29Z, same session as v0.75.0)", DO1-DO3).
**Tj's standing instruction**: save every agent's findings to GitHub as it finishes; usage can run out at any second; a new session on another Claude account must be able to resume with no data loss.

## What is saved, and where

| file | what |
| :- | :- |
| `results/<label>.json` | every FINISHED agent's structured result, one file each (never deleted by a later pass or session) |
| `FINDINGS.md` | all of them rendered readable (scouts first) |
| `journal.<run>.jsonl` | the raw journal of each run (redacted of key-shaped strings) |
| `partial/<label>.md` | a compact trace of an agent that was STILL RUNNING when the last save was made (what it had read and said); gone once the agent finishes |
| `STATUS.md` | finished and in-flight labels at the last save |
| `tools/research/rapid_sources_workflow.js` | the workflow script (resumable: `args.skip` = labels already done) |
| `tools/research/save_workflow.py` | the saver (below) |
| `tools/research/rapid_resume.py` | prints what is done / not done and the `args` to pass |

The workflow's shape: 10 scouts (`scout:<n>-<slug>`), then per source 2 skeptics (`verify-claims:<slug>`, `verify-fit:<slug>`), then `synth`, `critic`, up to 8 `gap:*` agents, then `final`.
Every agent is read-only research (GET only, no sign-ups, no keys, nothing from CNO: the prompts' HARD RULES).

## How a new session resumes (any account)

1. `bash tools/install-hooks.sh`; read CHECKPOINT.md and TASKS.md (DO1-DO3). `git pull` happened in the SessionStart hook.
2. `python3 tools/research/rapid_resume.py` — it says what is done and prints `{"skip": [...]}` on its last line. If it says ALL DONE, go to step 5.
3. Launch the workflow: `Workflow({scriptPath: "/home/user/novig/tools/research/rapid_sources_workflow.js", args: {"skip": [ ...the labels printed... ]}})` (pass `args` as a JSON value, not a string).
   Agents for finished labels are NOT run again; the agents downstream are told to READ those result files. An agent that was cut off is run again and reads `partial/<label>.md` first.
   (Do not use `resumeFromRunId`: it only works inside the session that started the run.)
4. Start the saver at once, with the transcript directory the Workflow tool prints ("Transcript dir: ..."):
   `cd /home/user/novig && setsid nohup python3 -I tools/research/save_workflow.py <TRANSCRIPT_DIR> research/rapid_sources_workflow_2026-10-07 "Rapid odds and scores: the ten sources Tj sent (<run id>)" > /tmp/saver.log 2>&1 < /dev/null &`
   It banks results within ~10 s of each agent finishing and an in-flight trace every ~90 s, commits only that folder and pushes. (Never `pkill -f save_workflow.py` from a shell whose own command line contains that text: it kills the shell.)
   Do not run two savers on one folder.
5. When `results/final.json` exists: write RESEARCH.md `## 115` from its `research_section_markdown` (check every number against `results/` first; the section's heading is in the prompt), tick DO1-DO3 in TASKS.md naming the evidence, run `bash tools/ckpt.sh`, and answer Tj: one short line per source (useful or not, why) and the one or two things worth building or testing on his phone, with the Release/commit link if code changed. Ask DL2 again (still unanswered).

## Rules that hold while resuming

- Read-only research: no sign-ups, no payments, no keys, nothing from crazyninjaodds.com, GET only, never a bookmaker's private API. Tj decides any key or plan.
- The repo is public: findings and numbers only; the saver redacts key-shaped text, the secret scan blocks a commit that still has one.
- Nothing in the app changes for this research (it is for Tj to decide what to build). If a build is asked for later, write it into TASKS.md first (CLAUDE.md "When Tj asks for something new").
