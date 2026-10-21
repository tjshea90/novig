#!/usr/bin/env bash
# test_volume_report.sh - the engine's volume report (TCL30d, XO2): tools/volume_report.py's selftest. A fast check: ckpt.sh runs it.
set -u
cd "$(dirname "$0")/.." || exit 1
out="$(timeout 120 python3 -I tools/volume_report.py selftest 2>&1)" || { echo "FAIL volume_report: $(printf '%s' "$out" | tail -3)"; exit 1; }
echo "ok   volume_report"
