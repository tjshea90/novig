#!/usr/bin/env bash
# test_rfq.sh - the parlay maker's tools (MK, MK12): the selftests of rfq_set.py, rfq_summary.py, rfq_legs.py and rfq_market.py. A fast check: ckpt.sh runs it.
set -u
cd "$(dirname "$0")/.." || exit 1
for t in rfq_set rfq_summary rfq_legs rfq_market; do
  out="$(timeout 60 python3 -I "tools/$t.py" selftest 2>&1)" || { echo "FAIL $t: $(printf '%s' "$out" | tail -3)"; exit 1; }
done
echo "ok   rfq tools"
