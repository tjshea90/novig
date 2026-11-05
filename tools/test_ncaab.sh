#!/usr/bin/env bash
# test_ncaab.sh - the college basketball against-the-spread tool (NC1): tools/ncaab_ats.py's selftest, and the golden cases the Kotlin signals are pinned to must be what the tool writes now.
# A fast check: ckpt.sh runs it.
set -u
cd "$(dirname "$0")/.." || exit 1
out="$(timeout 120 python3 -I tools/ncaab_ats.py selftest 2>&1)" || { echo "FAIL ncaab_ats: $(printf '%s' "$out" | tail -3)"; exit 1; }
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
python3 -I tools/ncaab_ats.py golden --data-dir "$tmp" >/dev/null 2>&1 || { echo "FAIL ncaab_ats golden: the tool could not write its golden cases"; exit 1; }
cmp -s "$tmp/signal-golden.json" data/src/test/resources/ncaab/signal-golden.json || { echo "FAIL ncaab_ats: data/src/test/resources/ncaab/signal-golden.json is stale (python3 -I tools/ncaab_ats.py golden --data-dir data/src/test/resources/ncaab)"; exit 1; }
echo "ok   ncaab_ats"
