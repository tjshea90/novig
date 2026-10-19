#!/usr/bin/env bash
# chat_watch.sh - kept so old instructions still work: the app chat's watcher is tools/chat.py watch now (rebuilt, CR, Tj 2026-10-19).
# Run under Monitor (persistent). On an account that is not the chat account it says so and exits.
cd "$(dirname "$0")/.." || exit 1
exec python3 -I -u tools/chat.py watch "$@"
