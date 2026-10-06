#!/usr/bin/env bash
# Fast check: the v0.70.1 analysis wave planner (tools/research/study_v0701/plan.py) still gates the phases right, and the verifier / phase-4 prompt writers still
# write what the planner waits for.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
python3 -I tools/research/study_v0701/plan.py --selftest
python3 -I tools/research/study_v0701/genverify.py --selftest
python3 -I tools/research/study_v0701/genphase4.py --selftest
