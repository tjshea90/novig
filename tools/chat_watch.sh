#!/usr/bin/env bash
# chat_watch.sh - wake this session when Tj writes in the phone app's chat (TCL29, Tj 2026-10-15).
# Run it under the Monitor tool (persistent): it costs NO model tokens until Tj sends something. Every ~15 s it asks GitHub for the head of `cloud-config` (one tiny
# `git ls-remote`); only when that changed does it fetch and print each chat/*.json from Tj that no reply answers yet, as one line:  CHAT <id> <text>
# Answer with:  python3 tools/tell_tj.py chat-reply <id> "your answer"   (the app shows it within seconds). Then write the request into TASKS.md as usual (CLAUDE.md).
# State is in /tmp only; a fresh watcher re-prints anything still unanswered.
set -u
cd "$(dirname "$0")/.." || exit 1
every="${CHAT_WATCH_EVERY:-6}"
last=""
lastrot=0
rotfile=/tmp/chat_watch_rotstate
while true; do
  # Who answers the chat now (Tj, 2026-10-16: the account scheduled at that time; a session hands the chat over when another account's slot is about to start). Every 2 minutes, no model tokens;
  # a line is printed only when the state CHANGES to SOON or STANDDOWN, or leaves STANDDOWN. See research/coordination/CHAT_OWNER.md for what to do on each.
  nowt=$(date +%s)
  if [ $((nowt - lastrot)) -ge 120 ]; then
    lastrot=$nowt
    line=$(timeout 60 python3 -I tools/rotation.py chat 2>/dev/null | head -1)
    st=$(printf '%s' "$line" | awk '{print $2}')
    prev=$(cat "$rotfile" 2>/dev/null || echo "")
    if [ -n "$st" ] && [ "$st" != "$prev" ]; then
      echo "$st" > "$rotfile"
      case "$st" in
        SOON|STANDDOWN) echo "$line" ;;
        *) [ "$prev" = "STANDDOWN" ] && echo "$line (the chat is yours again)" ;;
      esac
    fi
  fi
  head=$(git ls-remote origin refs/heads/cloud-config 2>/dev/null | cut -f1)
  if [ -n "$head" ] && [ "$head" != "$last" ]; then
    last="$head"
    if git fetch -q origin cloud-config 2>/dev/null; then
      python3 -I - <<'PY'
import json, subprocess, time
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
acked = {m.get("ack") for m in msgs if m.get("ack")}
seen_file = "/tmp/chat_watch_printed"
try:
    printed = set(open(seen_file).read().split())
except Exception:
    printed = set()
fresh_ack = []
now_ms = time.time() * 1000
def stale(m):
    # Tj, 2026-10-19: a woken session was answering OLD messages again (state is in /tmp, a fresh container re-printed every unanswered one). Such a message is already in TASKS.md
    # or was shown by an earlier watcher (its ack is in the repo). Show only: unseen messages under 24 h old, or seen-but-unanswered ones under 30 min old (a session cut off mid-answer).
    try:
        age = (now_ms - int(str(m.get("id", ""))[1:])) / 1000
    except Exception:
        return False
    return age > 86400 or (m.get("id") in acked and age > 1800)
for m in msgs:
    if m.get("from") == "tj" and not m.get("ack") and m.get("id") not in answered and m.get("id") not in printed and not stale(m):
        files = m.get("files") or []
        print("CHAT %s %s%s" % (m["id"], " ".join(str(m.get("text", "")).split())[:1500], ("  [ATTACHMENTS: %s]" % ", ".join(files)) if files else ""), flush=True)
        printed.add(m["id"])
        if m.get("id") not in acked:
            fresh_ack.append(m["id"])
open(seen_file, "w").write(" ".join(sorted(printed)))
# a thumbs-up under each message this watcher has just shown the session (Tj, 2026-10-16: mark messages Claude has read)
if fresh_ack:
    subprocess.run(["python3", "tools/tell_tj.py", "chat-ack", *fresh_ack], capture_output=True)
PY
    fi
  fi
  sleep "$every"
done
