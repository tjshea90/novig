#!/usr/bin/env bash
# Fast check (ckpt.sh runs every tools/test_*.sh): a key pasted into chat never reaches INBOX.md, and ordinary words, URLs and ids survive.
set -uo pipefail
D="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; cd "$D" || exit 1
fail=0
check() { # input, expected
  got="$(printf '%s' "$1" | python3 tools/redact_keys.py)"
  if [ "$got" != "$2" ]; then echo "redact_keys: '$1' -> '$got' (wanted '$2')"; fail=1; fi
}
check "my key is pk_live_9f8e7d6c5b4a39281706f5e4" "my key is [key redacted …f5e4]"
check "use abcdef0123456789abcdef0123456789 please" "use [key redacted …6789] please"
check "https://parlay-api.com/v1/sports/x/props?apiKey=Zq81kd92Lmx0" "https://parlay-api.com/v1/sports/x/props?apiKey=[key redacted …Lmx0]"
check "key: 4f3c2b1a0e9d8c7b" "key: [key redacted …7b]"
check "americanfootball_nfl and basketball_wnba_playoffs_2026" "americanfootball_nfl and basketball_wnba_playoffs_2026"
check "https://github.com/tjshea90/novig/releases/tag/v0.27.0" "https://github.com/tjshea90/novig/releases/tag/v0.27.0"
check "Review this diagnostic report: key …15d0: used 26, 19974 left" "Review this diagnostic report: key …15d0: used 26, 19974 left"
check "ccr-5babcffd-eylneu" "ccr-5babcffd-eylneu"
# The hook itself: a prompt with a key is written masked.
out="$(printf '{"prompt":"my ParlayAPI key sk9ZxQ2mB7vN4cR1tY8uW3eA6"}' | python3 -c '
import json, sys
sys.path.insert(0, "tools")
from redact_keys import redact
print(redact(json.load(sys.stdin)["prompt"]))')"
[ "$out" = "my ParlayAPI key [key redacted …eA6]" ] || { echo "redact_keys via hook shape: $out"; fail=1; }
exit $fail
