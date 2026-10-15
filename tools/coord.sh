#!/usr/bin/env bash
# coord.sh - thin wrapper so `bash tools/coord.sh <command>` works the same as `python3 -I tools/coord.py <command>`. See tools/coord.py and research/coordination/PROTOCOL.md.
exec python3 -I "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/coord.py" "$@"
