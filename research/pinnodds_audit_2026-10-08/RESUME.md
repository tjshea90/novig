# RESUME: the Pinnodds live-engine audit before real money (Tj, 2026-10-08)

**The question** (Tj, INBOX.md 2026-10-08T02:40Z): "Because I'll be using real money with this feature tonight, make sure it is catching truly positive EV odds live on novig and that there are
safeguards to refuse any bets that are negative EV or stale. It is not mandatory that this is a live only mode. [Profit] can be made by finding prematch odds movement on pinnodds web socket
before novig reacts and novig's odds are stale making one side positive EV on novig, this is also a good strategy for the app to incorporate."
**Tj's later instruction (same night):** "Pause the audit and save progress at the next best opportunity. Make a checkpoint so it can resume in another session" and "frequently save all progress
and checkpoint in anticipation of usage running out".

TASKS.md: **PX1-PX4**. The audit is PX1.

## What is saved, and where

| file | what |
| :- | :- |
| `results/<label>.json` | every FINISHED agent's structured result, one file each (`audit:<lens>`, `verify:<lens>:<finding #>:<skeptic #>`, `synthesize`) |
| `FINDINGS.md` | all of them rendered readable |
| `journal.<run>.jsonl` | the raw journal of each run |
| `partial/<label>.md` | a compact trace of an agent that was STILL RUNNING at the last save (what it had read and said) |
| `STATUS.md` | finished and in-flight labels at the last save |
| `tools/research/pinnodds_audit_workflow.js` | the workflow script, resumable: `args.skip` = labels already done (a cheap `load:` agent hands their saved result on) |
| `tools/research/save_workflow.py` | the saver (same one the ten-sources research used) |
| `tools/research/pinnodds_audit_resume.py` | prints what is done and the `args` to pass |
| `research/pinnodds_2026-10-08/{tape1,tape2}_raw_frames.ndjson.xz`, `pool.ndjson.gz` | the real data the lenses analyse (decompress COPIES into a scratch folder, never into the repo) |

The ten lenses: wrong-side, ev-fee-math, stale-pinnacle, stale-novig, trader-safety, market-equivalence, pinnbook-parsing, wiring-and-gates, statistics, pregame-design. Every medium-or-worse finding then gets
2-3 skeptic agents (REPRODUCE / ALREADY-PREVENTED / IMPACT), and one synthesizer writes the prioritized plan (safe defaults for tonight, the pregame spec, what should make Tj NOT turn real bets on).

## How a new session resumes (any account)

1. `bash tools/install-hooks.sh`; read CHECKPOINT.md and TASKS.md (PX1-PX4). `git pull` happened in the SessionStart hook.
2. `python3 tools/research/pinnodds_audit_resume.py`: it prints what is done and, on its last line, `{"skip": [...]}`.
3. Launch: `Workflow({scriptPath: "/home/user/novig/tools/research/pinnodds_audit_workflow.js", args: {"skip": [ ...the labels printed... ]}})` (args as a JSON value, not a string). Do NOT use `resumeFromRunId`
   (it only works in the session that started the run). This box has 4 CPUs, so only 2 agents run at once: the whole audit is slow (about 1-2 hours). Lens agents are independent: if time is short,
   skip the verify phase by passing `skip` for nothing and watching `results/`, or read `FINDINGS.md` as it grows.
4. Start the saver at once, with the transcript directory the Workflow tool prints:
   `cd /home/user/novig && setsid nohup python3 -I tools/research/save_workflow.py <TRANSCRIPT_DIR> research/pinnodds_audit_2026-10-08 "Pinnodds live engine audit before real money (<run id>)" > /tmp/saver_audit.log 2>&1 < /dev/null &`
   (Never `pkill -f save_workflow.py` from a shell whose own command line contains that text. One saver per folder.)
5. When `results/synthesize.json` exists: implement its plan (PX2) with tests, write RESEARCH.md `## 117`, tick PX1 in TASKS.md naming the evidence.

## Rules while resuming

- The agents are READ-ONLY on the repo. They never open the Pinnodds websocket nor use the Pinnodds key (ONE socket per account; a second connection evicts Tj's phone). Novig public REST at most 3 requests/s.
- The repo is public: findings and numbers only; the saver redacts key-shaped text and the secret scan blocks a commit that still has one.
- **The code may have changed since the audit started** (v0.76.1): the guards from Tj's request were being built while the audit was paused. Findings cite v0.76.1 line numbers; read `git log -- data/src/main/kotlin/com/tjshea/vigilant/data/pinnodds`
  first, and treat a finding as ALREADY FIXED only if a test in `data/src/test/kotlin/.../pinnodds/` pins the fix.

## State of the implementation when the audit was paused (updated by every checkpoint; see CHECKPOINT.md for the latest)

- Pre-audit analysis by the main session (numbers in `research/pinnodds_2026-10-08/pre_audit_analysis.txt`): on the 52-minute pool, the biggest "edges" are mostly stale Pinnacle lines of finished games or one-tick
  spikes that reverted (decision EV above 12% realized about -1% to -30% against Pinnacle's later fair; "standing" disagreements of +60% to +11,000% are Pinnacle lines frozen after the game ended).
  Safeguards derived from it: an EV window, line re-confirmation freshness, Novig-before-the-move alignment, a decision-age cap, STANDING mode never bets real money.
