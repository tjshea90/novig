#!/usr/bin/env bash
# chat_watch.sh - wake this session when Tj writes in the phone app's chat (TCL29, Tj 2026-10-15).
# Run it under the Monitor tool (persistent): it costs NO model tokens until Tj sends something. Every ~15 s it asks GitHub for the head of `cloud-config` (one tiny
# `git ls-remote`); only when that changed does it fetch and print each chat/*.json from Tj that no reply answers yet, as one line:  CHAT <id> <text>
# Answer with:  python3 tools/tell_tj.py chat-reply <id> "your answer"   (the app shows it within seconds). Then write the request into TASKS.md as usual (CLAUDE.md).
# State is in /tmp only; a fresh watcher re-prints anything still unanswered.
set -u
cd "$(dirname "$0")/.." || exit 1
every="${CHAT_WATCH_EVERY:-15}"
last=""
while true; do
  head=$(git ls-remote origin refs/heads/cloud-config 2>/dev/null | cut -f1)
  if [ -n "$head" ] && [ "$head" != "$last" ]; then
    last="$head"
    if git fetch -q origin cloud-config 2>/dev/null; then
      python3 -I - <<'PY'
import json, subprocess
def sh(*a):
    return subprocess.run(a, capture_output=True, text=True).stdout
names = [n for n in sh("git", "ls-tree", "--name-only", "FETCH_HEAD", "chat/").split() if n.endswith(".json")]
msgs = []
for n in sorted(names):
    try:
        msgs.append(json.loads(sh("git", "show", "FETCH_HEAD:" + n)))
    except Exception:
        pass
answered = {m.get("replyTo") for m in msgs if m.get("replyTo")}
for m in msgs:
    if m.get("from") == "tj" and m.get("id") not in answered:
        print("CHAT %s %s" % (m["id"], " ".join(str(m.get("text", "")).split())[:1500]), flush=True)
PY
    fi
  fi
  sleep "$every"
done
