#!/usr/bin/env bash
# test_chat.sh - the app chat's tools (CR, Tj 2026-10-19): chat.py, chat_wake.py, and the callers that write through them (tell_tj.py, deadman.py). A fast check: ckpt.sh runs it.
set -u
cd "$(dirname "$0")/.." || exit 1
rc=0
for t in chat chat_wake tell_tj deadman; do
  out="$(timeout 300 python3 -I "tools/$t.py" selftest 2>&1)" || { echo "FAIL $t: $(printf '%s' "$out" | tail -3)"; rc=1; continue; }
  echo "ok   $t"
done
exit $rc
