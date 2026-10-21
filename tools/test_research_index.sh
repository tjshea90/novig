#!/usr/bin/env bash
# test_research_index.sh - the research library's index is current and LEVERS.md points at real files (Tj, 2026-10-21). A fast check: ckpt.sh runs it.
set -u
cd "$(dirname "$0")/.." || exit 1
out="$(timeout 120 python3 -I tools/research_index.py selftest 2>&1 && timeout 120 python3 -I tools/research_index.py check 2>&1)" || { echo "FAIL research_index: $(printf '%s' "$out" | tail -3)"; exit 1; }
echo "ok   research_index"
