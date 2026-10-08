# STATUS of Pinnodds live engine audit before real money (wf_e596e2b2-378)

Run `wf_e596e2b2-378`. Finished agents with a saved result: 1.

FINISHED (results/<label>.json):
- audit:ev-fee-math

IN FLIGHT when last saved (partial/<label>.md has what each had read):
- audit:wrong-side
- audit:stale-pinnacle
- audit:stale-novig
- audit:trader-safety
- audit:market-equivalence
- audit:pinnbook-parsing
- audit:wiring-and-gates
- audit:statistics
- audit:pregame-design
- verify:ev-fee-math:0:0
- verify:ev-fee-math:0:1
- verify:ev-fee-math:1:0
- verify:ev-fee-math:1:1
- synthesize

RESUME: see RESUME.md in this folder (a new session, any account: `python3 tools/research/rapid_resume.py` prints the Workflow args).
