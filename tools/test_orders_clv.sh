#!/usr/bin/env bash
# test_orders_clv.sh - the CLV of the engine's own orders at their fill price (CH57): tools/orders_clv.py's selftest. A fast check: ckpt.sh runs it.
set -u
cd "$(dirname "$0")/.." || exit 1
out="$(timeout 120 python3 -I tools/orders_clv.py selftest 2>&1)" || { echo "FAIL orders_clv: $(printf '%s' "$out" | tail -3)"; exit 1; }
echo "ok   orders_clv"
